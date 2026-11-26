package com.hongcheng.shop.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 详情缓存组件：Redis 缓存三防。
 * <ul>
 *   <li>防穿透：DB 查不到也写入空值标记 {@link #NULL_MARKER}（短 TTL），
 *       恶意请求不存在 id 时不再反复打到 MySQL；</li>
 *   <li>防击穿：缓存失效瞬间用 setIfAbsent 互斥锁只放一个线程回源重建，
 *       其余线程短暂等待后重读缓存，重试耗尽则降级直连 DB（不写缓存）；</li>
 *   <li>防雪崩：基础 TTL 1800s + 0~600s 随机抖动，避免同一批 key 同时过期。</li>
 *   <li>故障降级：Redis 连接失败时熔断 10s，窗口内读请求直连 DB、写缓存跳过，
 *       详情接口不因缓存组件不可用而 500；窗口内失败的 evict 记入待补偿集合，
 *       Redis 恢复后首次访问时补删，避免管理端改动的旧缓存残留。</li>
 * </ul>
 * 缓存一致性：管理端写操作成功后调用 {@link #evict(String)} 删除缓存（Cache-Aside）。
 * <p>
 * TTL/重试参数通过 {@code hc.shop.cache.*} 注入，并由 {@link RefreshScope} 支持 Nacos 热更新：
 * 修改配置后本 Bean 会重建，下一次写缓存立即使用新 TTL，无需重启服务。
 */
@Slf4j
@RefreshScope
@Component
@RequiredArgsConstructor
public class ShopCacheService {

    public static final String SHOP_DETAIL_KEY_PREFIX = "shop:detail:";
    public static final String ITEM_DETAIL_KEY_PREFIX = "shop:item:";

    /** 空值缓存标记（防穿透占位符，与正常 JSON 值可区分） */
    public static final String NULL_MARKER = "NULL";

    /** 基础 TTL：30 分钟（Nacos 可热更新） */
    @Value("${hc.shop.cache.base-ttl-seconds:1800}")
    private long baseTtlSeconds = 1800L;
    /** TTL 随机抖动上限：10 分钟（防雪崩，Nacos 可热更新） */
    @Value("${hc.shop.cache.ttl-jitter-seconds:600}")
    private long ttlJitterSeconds = 600L;
    /** 空值标记 TTL：60s + 0~30s 抖动，尽快自愈（Nacos 可热更新） */
    @Value("${hc.shop.cache.null-ttl-seconds:60}")
    private long nullTtlSeconds = 60L;
    @Value("${hc.shop.cache.null-ttl-jitter-seconds:30}")
    private long nullTtlJitterSeconds = 30L;
    /** 互斥锁持有上限：重建线程崩溃也能自动释放，避免死锁（Nacos 可热更新） */
    @Value("${hc.shop.cache.lock-ttl-seconds:10}")
    private long lockTtlSeconds = 10L;
    /** 未拿到锁时的重试次数与间隔（Nacos 可热更新） */
    @Value("${hc.shop.cache.retry-times:3}")
    private int retryTimes = 3;
    @Value("${hc.shop.cache.retry-sleep-millis:50}")
    private long retrySleepMillis = 50L;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** Redis 连接失败后的熔断窗口：窗口内绕过 Redis 直连 DB，避免每请求重复等待连接 */
    private static final long REDIS_SKIP_MILLIS = 10_000L;
    private volatile long redisSkipUntilMillis;
    /** 降级期间失败的 evict：恢复后补删，保证 Cache-Aside 一致性最终成立 */
    private final Set<String> pendingEvicts = ConcurrentHashMap.newKeySet();

    public static String shopKey(Long id) {
        return SHOP_DETAIL_KEY_PREFIX + id;
    }

    public static String itemKey(Long id) {
        return ITEM_DETAIL_KEY_PREFIX + id;
    }

    /**
     * 读缓存，未命中则按互斥锁策略回源加载并写缓存。
     *
     * @param loader DB 加载器，查不到返回 null（会写入空值标记）
     * @return 缓存/DB 中的值；null 表示确认不存在（含空值标记命中）
     */
    public <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader) {
        if (redisSkipping()) {
            return loader.get();
        }
        drainPendingEvicts();
        if (redisSkipping()) {
            return loader.get();
        }
        CacheResult<T> result = tryRead(key, type);
        if (result.hit()) {
            return result.value();
        }
        if (redisSkipping()) {
            return loader.get();
        }

        String lockKey = key + ":lock";
        Boolean locked;
        try {
            locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", lockTtlSeconds, TimeUnit.SECONDS);
        } catch (RedisConnectionFailureException e) {
            tripRedisBreaker(key, e);
            return loader.get();
        }
        if (Boolean.TRUE.equals(locked)) {
            try {
                // 双重检查：拿锁间隙可能已有其他线程完成重建
                result = tryRead(key, type);
                if (result.hit()) {
                    return result.value();
                }
                T value = loader.get();
                writeCache(key, value);
                log.info("缓存重建 key={}, nullValue={}", key, value == null);
                return value;
            } finally {
                try {
                    redisTemplate.delete(lockKey);
                } catch (RedisConnectionFailureException e) {
                    tripRedisBreaker(lockKey, e);
                }
            }
        }

        // 未拿到锁：等待重建完成后重读缓存；重试耗尽降级直连 DB（只读不写，避免与持锁者竞争）
        for (int i = 0; i < retryTimes; i++) {
            sleepQuietly();
            result = tryRead(key, type);
            if (result.hit()) {
                return result.value();
            }
            if (redisSkipping()) {
                return loader.get();
            }
        }
        log.warn("缓存重建未拿到互斥锁，降级直连 DB key={}", key);
        return loader.get();
    }

    /** 写操作后删除缓存（Cache-Aside 一致性策略）；Redis 不可用时记入待补偿集合 */
    public void evict(String key) {
        if (redisSkipping()) {
            pendingEvicts.add(key);
            log.warn("Redis 降级窗口，evict 待补偿 key={}", key);
            return;
        }
        try {
            redisTemplate.delete(key);
            log.debug("缓存删除 key={}", key);
        } catch (RedisConnectionFailureException e) {
            pendingEvicts.add(key);
            tripRedisBreaker(key, e);
        }
    }

    private boolean redisSkipping() {
        return System.currentTimeMillis() < redisSkipUntilMillis;
    }

    private void tripRedisBreaker(String key, RedisConnectionFailureException e) {
        redisSkipUntilMillis = System.currentTimeMillis() + REDIS_SKIP_MILLIS;
        log.warn("Redis 连接失败，进入 {}ms 降级窗口 key={}", REDIS_SKIP_MILLIS, key, e);
    }

    private void drainPendingEvicts() {
        if (pendingEvicts.isEmpty()) {
            return;
        }
        for (String key : Set.copyOf(pendingEvicts)) {
            try {
                redisTemplate.delete(key);
                pendingEvicts.remove(key);
                log.info("补偿删除降级期间未失效的缓存 key={}", key);
            } catch (RedisConnectionFailureException e) {
                tripRedisBreaker(key, e);
                return;
            }
        }
    }

    private <T> void writeCache(String key, T value) {
        try {
            if (value == null) {
                long ttl = nullTtlSeconds + ThreadLocalRandom.current().nextLong(nullTtlJitterSeconds + 1);
                redisTemplate.opsForValue().set(key, NULL_MARKER, ttl, TimeUnit.SECONDS);
                return;
            }
            long ttl = baseTtlSeconds + ThreadLocalRandom.current().nextLong(ttlJitterSeconds + 1);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            // 缓存写失败不影响主流程，下次请求会再次回源
            log.error("缓存序列化失败 key={}", key, e);
        } catch (RedisConnectionFailureException e) {
            tripRedisBreaker(key, e);
        }
    }

    private <T> CacheResult<T> tryRead(String key, Class<T> type) {
        if (redisSkipping()) {
            return new CacheResult<>(false, null);
        }
        String cached;
        try {
            cached = redisTemplate.opsForValue().get(key);
        } catch (RedisConnectionFailureException e) {
            tripRedisBreaker(key, e);
            return new CacheResult<>(false, null);
        }
        if (cached == null) {
            return new CacheResult<>(false, null);
        }
        if (NULL_MARKER.equals(cached)) {
            return new CacheResult<>(true, null);
        }
        try {
            return new CacheResult<>(true, objectMapper.readValue(cached, type));
        } catch (JsonProcessingException e) {
            // 脏数据自愈：删掉后按未命中处理
            log.error("缓存反序列化失败，删除脏数据 key={}", key, e);
            try {
                redisTemplate.delete(key);
            } catch (RedisConnectionFailureException ex) {
                tripRedisBreaker(key, ex);
            }
            return new CacheResult<>(false, null);
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(retrySleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 三态读结果：hit=false 未命中；hit=true 且 value=null 表示空值标记命中 */
    private record CacheResult<T>(boolean hit, T value) {
    }
}
package com.hongcheng.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.order.dto.OrderCreateStateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 下单请求的暂存状态（Redis db4）。
 * <p>
 * 削峰后订单在 MQ 中排队，DB 尚无订单行；客户端凭 orderNo 轮询 /order/state 时
 * 由这里告知「排队中 / 创建成功 / 创建失败（如库存不足）」。
 * 建单成功后 DB 为最终事实来源，此状态只做短暂过渡。
 * <p>
 * 故障降级：Redis 连接失败时熔断 10s。窗口内写入跳过（不阻断入队/建单主流程），
 * 读取返回合成 QUEUED——轮询方会继续等到 DB 建单后拿 CREATED，
 * 而不是误判 NOT_FOUND 提前终止；DB 已有订单时 {@code OrderService.state} 根本不会读这里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStateStore {

    public static final String STATE_QUEUED = "QUEUED";
    public static final String STATE_CREATED = "CREATED";
    public static final String STATE_FAILED = "FAILED";
    public static final String STATE_NOT_FOUND = "NOT_FOUND";

    private static final String KEY_PREFIX = "order:create:";
    private static final Duration QUEUED_TTL = Duration.ofMinutes(30);
    private static final Duration CREATED_TTL = Duration.ofMinutes(5);
    private static final Duration FAILED_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** Redis 连接失败后的熔断窗口：窗口内跳过写入、读取返回合成 QUEUED */
    private static final long REDIS_SKIP_MILLIS = 10_000L;
    private volatile long redisSkipUntilMillis;

    public void markQueued(String orderNo) {
        markQueued(orderNo, null);
    }

    public void markQueued(String orderNo, Long userId) {
        put(orderNo, STATE_QUEUED, "已进入下单队列，等待处理", QUEUED_TTL, userId);
    }

    public void markCreated(String orderNo) {
        markCreated(orderNo, null);
    }

    public void markCreated(String orderNo, Long userId) {
        put(orderNo, STATE_CREATED, "订单创建成功", CREATED_TTL, userId);
    }

    public void markFailed(String orderNo, String message) {
        markFailed(orderNo, null, message);
    }

    public void markFailed(String orderNo, Long userId, String message) {
        put(orderNo, STATE_FAILED, message, FAILED_TTL, userId);
    }

    /** 读取暂存状态；不存在/脏数据返回 null（脏数据顺手删除）；Redis 不可用返回合成 QUEUED */
    public OrderCreateStateVO get(String orderNo) {
        if (redisSkipping()) {
            return syntheticQueued(orderNo);
        }
        String json;
        try {
            json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + orderNo);
        } catch (RedisConnectionFailureException e) {
            tripRedisBreaker(orderNo, e);
            return syntheticQueued(orderNo);
        }
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, OrderCreateStateVO.class);
        } catch (JsonProcessingException e) {
            log.warn("下单状态缓存解析失败，已删除 orderNo={}", orderNo);
            stringRedisTemplate.delete(KEY_PREFIX + orderNo);
            return null;
        }
    }

    private void put(String orderNo, String state, String message, Duration ttl, Long userId) {
        if (redisSkipping()) {
            log.warn("Redis 降级窗口，跳过下单状态写入 orderNo={}, state={}", orderNo, state);
            return;
        }
        OrderCreateStateVO vo = new OrderCreateStateVO(orderNo, state, message, LocalDateTime.now(), userId);
        try {
            stringRedisTemplate.opsForValue()
                    .set(KEY_PREFIX + orderNo, objectMapper.writeValueAsString(vo), ttl);
        } catch (JsonProcessingException e) {
            log.error("下单状态缓存写入失败 orderNo={}", orderNo, e);
        } catch (RedisConnectionFailureException e) {
            tripRedisBreaker(orderNo, e);
        }
    }

    private OrderCreateStateVO syntheticQueued(String orderNo) {
        return new OrderCreateStateVO(orderNo, STATE_QUEUED,
                "排队状态缓存暂不可用，正在以数据库为准确认", LocalDateTime.now());
    }

    private boolean redisSkipping() {
        return System.currentTimeMillis() < redisSkipUntilMillis;
    }

    private void tripRedisBreaker(String orderNo, RedisConnectionFailureException e) {
        redisSkipUntilMillis = System.currentTimeMillis() + REDIS_SKIP_MILLIS;
        log.warn("Redis 连接失败，进入 {}ms 降级窗口 orderNo={}", REDIS_SKIP_MILLIS, orderNo, e);
    }
}

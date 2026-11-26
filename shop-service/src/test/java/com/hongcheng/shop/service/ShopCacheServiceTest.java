package com.hongcheng.shop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.shop.dto.ShopVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缓存三防单元测试：命中 / 空值标记防穿透 / 互斥锁重建防击穿 / TTL 抖动防雪崩 / 未拿锁降级。
 */
class ShopCacheServiceTest {

    private static final String KEY = "shop:detail:1";
    private static final String LOCK_KEY = KEY + ":lock";

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ShopCacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        cacheService = new ShopCacheService(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("缓存命中 - 直接反序列化返回，不回源不写缓存")
    void getOrLoad_whenCacheHit_returnsParsedValueWithoutLoader() throws Exception {
        // given
        ShopVO cached = new ShopVO();
        cached.setId(1L);
        cached.setShopName("洪城养车·青山湖旗舰店");
        when(valueOps.get(KEY)).thenReturn(objectMapper.writeValueAsString(cached));
        AtomicInteger loaderCalls = new AtomicInteger();

        // when
        ShopVO result = cacheService.getOrLoad(KEY, ShopVO.class, () -> {
            loaderCalls.incrementAndGet();
            return null;
        });

        // then
        assertThat(result.getShopName()).isEqualTo("洪城养车·青山湖旗舰店");
        assertThat(loaderCalls.get()).isZero();
        verify(valueOps, never()).set(eq(KEY), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("空值标记命中 - 返回 null 且不回源（防穿透）")
    void getOrLoad_whenNullMarkerHit_returnsNullWithoutLoader() {
        // given
        when(valueOps.get(KEY)).thenReturn(ShopCacheService.NULL_MARKER);
        AtomicInteger loaderCalls = new AtomicInteger();

        // when
        ShopVO result = cacheService.getOrLoad(KEY, ShopVO.class, () -> {
            loaderCalls.incrementAndGet();
            return null;
        });

        // then
        assertThat(result).isNull();
        assertThat(loaderCalls.get()).isZero();
    }

    @Test
    @DisplayName("缓存未命中且拿到互斥锁 - 回源重建并写带抖动的 TTL")
    void getOrLoad_whenMissAndLockAcquired_rebuildsWithJitterTtl() {
        // given
        when(valueOps.get(KEY)).thenReturn(null);
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(10L), eq(TimeUnit.SECONDS))).thenReturn(true);
        ShopVO dbValue = new ShopVO();
        dbValue.setId(1L);
        dbValue.setShopName("洪城养车·红谷滩店");
        Supplier<ShopVO> loader = () -> dbValue;

        // when
        ShopVO result = cacheService.getOrLoad(KEY, ShopVO.class, loader);

        // then
        assertThat(result).isSameAs(dbValue);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(valueOps).set(eq(KEY), json.capture(), ttl.capture(), eq(TimeUnit.SECONDS));
        assertThat(json.getValue()).contains("洪城养车·红谷滩店");
        assertThat(ttl.getValue()).isBetween(1800L, 2400L);
        verify(redisTemplate).delete(LOCK_KEY);
    }

    @Test
    @DisplayName("DB 查无记录 - 写入空值标记，短 TTL（防穿透）")
    void getOrLoad_whenDbMiss_writesNullMarkerWithShortTtl() {
        // given
        when(valueOps.get(KEY)).thenReturn(null);
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(10L), eq(TimeUnit.SECONDS))).thenReturn(true);

        // when
        ShopVO result = cacheService.getOrLoad(KEY, ShopVO.class, () -> null);

        // then
        assertThat(result).isNull();
        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(valueOps).set(eq(KEY), eq(ShopCacheService.NULL_MARKER), ttl.capture(), eq(TimeUnit.SECONDS));
        assertThat(ttl.getValue()).isBetween(60L, 90L);
    }

    @Test
    @DisplayName("未拿到互斥锁 - 重试后降级直连 DB，不写缓存（防击穿）")
    void getOrLoad_whenLockNotAcquired_fallsBackToDbWithoutCacheWrite() {
        // given
        when(valueOps.get(KEY)).thenReturn(null);
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(10L), eq(TimeUnit.SECONDS))).thenReturn(false);
        ShopVO dbValue = new ShopVO();
        dbValue.setId(1L);
        AtomicInteger loaderCalls = new AtomicInteger();

        // when
        ShopVO result = cacheService.getOrLoad(KEY, ShopVO.class, () -> {
            loaderCalls.incrementAndGet();
            return dbValue;
        });

        // then
        assertThat(result).isSameAs(dbValue);
        assertThat(loaderCalls.get()).isEqualTo(1);
        // 初始读 + 3 次重试 = 4 次读缓存
        verify(valueOps, org.mockito.Mockito.times(4)).get(KEY);
        verify(valueOps, never()).set(eq(KEY), anyString(), anyLong(), any(TimeUnit.class));
        verify(redisTemplate, never()).delete(LOCK_KEY);
    }

    @Test
    @DisplayName("脏缓存数据 - 删除后按未命中重建（自愈）")
    void getOrLoad_whenDirtyData_deletesAndRebuilds() {
        // given
        when(valueOps.get(KEY)).thenReturn("not-a-json");
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(10L), eq(TimeUnit.SECONDS))).thenReturn(true);
        ShopVO dbValue = new ShopVO();
        dbValue.setId(1L);
        AtomicInteger loaderCalls = new AtomicInteger();

        // when
        ShopVO result = cacheService.getOrLoad(KEY, ShopVO.class, () -> {
            loaderCalls.incrementAndGet();
            return dbValue;
        });

        // then
        assertThat(result).isSameAs(dbValue);
        assertThat(loaderCalls.get()).isEqualTo(1);
        verify(redisTemplate, org.mockito.Mockito.atLeastOnce()).delete(KEY);
    }

    @Test
    @DisplayName("Redis 连接失败 - 降级直连 DB，不再尝试锁与写缓存")
    void getOrLoad_whenRedisConnectionFails_fallsBackToDb() {
        // given
        when(valueOps.get(KEY)).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"));
        ShopVO dbValue = new ShopVO();
        dbValue.setId(1L);

        // when
        ShopVO result = cacheService.getOrLoad(KEY, ShopVO.class, () -> dbValue);

        // then
        assertThat(result).isSameAs(dbValue);
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        verify(valueOps, never()).set(eq(KEY), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("熔断窗口内 - 第二次请求完全绕过 Redis 直连 DB")
    void getOrLoad_withinBreakerWindow_skipsRedisEntirely() {
        // given
        when(valueOps.get(KEY)).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"));
        ShopVO dbValue = new ShopVO();
        dbValue.setId(1L);
        cacheService.getOrLoad(KEY, ShopVO.class, () -> dbValue);

        // when
        ShopVO second = cacheService.getOrLoad(KEY, ShopVO.class, () -> dbValue);

        // then
        assertThat(second).isSameAs(dbValue);
        verify(valueOps, org.mockito.Mockito.times(1)).get(KEY);
    }

    @Test
    @DisplayName("降级期间 evict 失败 - 记入待补偿，恢复后首次访问补删")
    void evict_whenRedisDown_drainsPendingAfterRecovery() {
        // given
        when(redisTemplate.delete(KEY))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"))
                .thenReturn(true);
        cacheService.evict(KEY);
        org.springframework.test.util.ReflectionTestUtils.setField(cacheService, "redisSkipUntilMillis", 0L);
        when(valueOps.get(KEY)).thenReturn(null);
        when(valueOps.setIfAbsent(eq(LOCK_KEY), anyString(), eq(10L), eq(TimeUnit.SECONDS))).thenReturn(true);
        ShopVO dbValue = new ShopVO();
        dbValue.setId(1L);

        // when
        ShopVO result = cacheService.getOrLoad(KEY, ShopVO.class, () -> dbValue);

        // then
        assertThat(result).isSameAs(dbValue);
        verify(redisTemplate, org.mockito.Mockito.times(2)).delete(KEY);
    }
}
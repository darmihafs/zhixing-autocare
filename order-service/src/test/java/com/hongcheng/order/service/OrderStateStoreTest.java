package com.hongcheng.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.order.dto.OrderCreateStateVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单暂存状态单元测试：正常读写 / 空键 / Redis 故障降级（写跳过、读合成 QUEUED、熔断窗口）。
 */
class OrderStateStoreTest {

    private static final String ORDER_NO = "HC2123000000000000001";
    private static final String KEY = "order:create:" + ORDER_NO;

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private OrderStateStore stateStore;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        stateStore = new OrderStateStore(redisTemplate,
                new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));
    }

    @Test
    @DisplayName("正常写入 - QUEUED 状态带 30 分钟 TTL")
    void markQueued_writesStateWithTtl() {
        // when
        stateStore.markQueued(ORDER_NO);

        // then
        verify(valueOps).set(eq(KEY), anyString(), eq(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("键不存在 - 返回 null 交由调用方判定 NOT_FOUND")
    void get_whenKeyMissing_returnsNull() {
        // given
        when(valueOps.get(KEY)).thenReturn(null);

        // when / then
        assertThat(stateStore.get(ORDER_NO)).isNull();
    }

    @Test
    @DisplayName("Redis 连接失败 - 写入跳过不抛异常，不阻断入队主流程")
    void markQueued_whenRedisConnectionFails_doesNotThrow() {
        // given
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        // when / then
        assertThatCode(() -> stateStore.markQueued(ORDER_NO)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis 连接失败 - 读取返回合成 QUEUED，轮询不会误判 NOT_FOUND")
    void get_whenRedisConnectionFails_returnsSyntheticQueued() {
        // given
        when(valueOps.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));

        // when
        OrderCreateStateVO state = stateStore.get(ORDER_NO);

        // then
        assertThat(state.getState()).isEqualTo(OrderStateStore.STATE_QUEUED);
        assertThat(state.getOrderNo()).isEqualTo(ORDER_NO);
    }

    @Test
    @DisplayName("熔断窗口内 - 后续读写完全绕过 Redis")
    void withinBreakerWindow_skipsRedisEntirely() {
        // given
        when(valueOps.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));
        stateStore.get(ORDER_NO);

        // when
        OrderCreateStateVO second = stateStore.get(ORDER_NO);
        stateStore.markCreated(ORDER_NO);

        // then
        assertThat(second.getState()).isEqualTo(OrderStateStore.STATE_QUEUED);
        verify(valueOps, times(1)).get(KEY);
        verify(valueOps, times(0)).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("带 userId 写入暂存状态 - JSON 记录归属用户供 IDOR 校验")
    void markQueuedWithUserId_serializesOwner() {
        stateStore.markQueued(ORDER_NO, 42L);

        org.mockito.ArgumentCaptor<String> jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(KEY), jsonCaptor.capture(), eq(Duration.ofMinutes(30)));
        assertThat(jsonCaptor.getValue()).contains("\"userId\":42");
    }
}
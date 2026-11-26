package com.hongcheng.order.state;

import com.hongcheng.common.enums.OrderStatusEnum;
import com.hongcheng.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单状态机流转表测试。
 */
class OrderStateMachineTest {

    @Test
    @DisplayName("待支付可流转到已支付/已取消")
    void canTransit_fromPendingPayment_allowsPaidAndCancelled() {
        assertThat(OrderStateMachine.canTransit(OrderStatusEnum.PENDING_PAYMENT.getCode(),
                OrderStatusEnum.PAID.getCode())).isTrue();
        assertThat(OrderStateMachine.canTransit(OrderStatusEnum.PENDING_PAYMENT.getCode(),
                OrderStatusEnum.CANCELLED.getCode())).isTrue();
    }

    @Test
    @DisplayName("已支付可流转到服务中/退款中，服务中可流转到已完成/退款中")
    void canTransit_fromPaidAndInService() {
        assertThat(OrderStateMachine.canTransit(OrderStatusEnum.PAID.getCode(),
                OrderStatusEnum.IN_SERVICE.getCode())).isTrue();
        assertThat(OrderStateMachine.canTransit(OrderStatusEnum.PAID.getCode(),
                OrderStatusEnum.REFUNDING.getCode())).isTrue();
        assertThat(OrderStateMachine.canTransit(OrderStatusEnum.IN_SERVICE.getCode(),
                OrderStatusEnum.COMPLETED.getCode())).isTrue();
        assertThat(OrderStateMachine.canTransit(OrderStatusEnum.IN_SERVICE.getCode(),
                OrderStatusEnum.REFUNDING.getCode())).isTrue();
        assertThat(OrderStateMachine.canTransit(OrderStatusEnum.REFUNDING.getCode(),
                OrderStatusEnum.REFUNDED.getCode())).isTrue();
    }

    @Test
    @DisplayName("跳过支付直达完成、已取消再支付等非法流转被拒绝")
    void canTransit_illegalTransitions_rejected() {
        assertThat(OrderStateMachine.canTransit(OrderStatusEnum.PENDING_PAYMENT.getCode(),
                OrderStatusEnum.COMPLETED.getCode())).isFalse();
        assertThat(OrderStateMachine.canTransit(OrderStatusEnum.CANCELLED.getCode(),
                OrderStatusEnum.PAID.getCode())).isFalse();
        assertThat(OrderStateMachine.canTransit(OrderStatusEnum.PAID.getCode(),
                OrderStatusEnum.CANCELLED.getCode())).isFalse();
        assertThat(OrderStateMachine.canTransit(OrderStatusEnum.COMPLETED.getCode(),
                OrderStatusEnum.PAID.getCode())).isFalse();
        assertThat(OrderStateMachine.canTransit(null, OrderStatusEnum.PAID.getCode())).isFalse();
    }

    @Test
    @DisplayName("非法流转抛 1302 业务异常并携带原/目标状态")
    void assertTransit_illegal_throwsBusinessException() {
        assertThatThrownBy(() -> OrderStateMachine.assertTransit(
                OrderStatusEnum.CANCELLED.getCode(), OrderStatusEnum.PAID.getCode()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getCode()).isEqualTo(1302);
                    assertThat(ex.getMessage()).contains("40").contains("10");
                });
    }
}

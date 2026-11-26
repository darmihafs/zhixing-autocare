package com.hongcheng.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 状态枚举单元测试：code 与建表 SQL 注释一一对应。
 */
class StatusEnumTest {

    @Test
    @DisplayName("OrderStatusEnum - 7 个状态码与 SQL 定义一致")
    void orderStatus_codesMatchSchema() {
        assertThat(OrderStatusEnum.of(0)).isEqualTo(OrderStatusEnum.PENDING_PAYMENT);
        assertThat(OrderStatusEnum.of(10)).isEqualTo(OrderStatusEnum.PAID);
        assertThat(OrderStatusEnum.of(20)).isEqualTo(OrderStatusEnum.IN_SERVICE);
        assertThat(OrderStatusEnum.of(30)).isEqualTo(OrderStatusEnum.COMPLETED);
        assertThat(OrderStatusEnum.of(40)).isEqualTo(OrderStatusEnum.CANCELLED);
        assertThat(OrderStatusEnum.of(50)).isEqualTo(OrderStatusEnum.REFUNDING);
        assertThat(OrderStatusEnum.of(60)).isEqualTo(OrderStatusEnum.REFUNDED);
        assertThat(OrderStatusEnum.values()).hasSize(7);
    }

    @Test
    @DisplayName("PayStatusEnum - 4 个状态码与 SQL 定义一致")
    void payStatus_codesMatchSchema() {
        assertThat(PayStatusEnum.of(0)).isEqualTo(PayStatusEnum.PAYING);
        assertThat(PayStatusEnum.of(1)).isEqualTo(PayStatusEnum.SUCCESS);
        assertThat(PayStatusEnum.of(2)).isEqualTo(PayStatusEnum.FAILED);
        assertThat(PayStatusEnum.of(3)).isEqualTo(PayStatusEnum.CLOSED);
        assertThat(PayStatusEnum.values()).hasSize(4);
    }

    @Test
    @DisplayName("of(null/未知code) - 返回 null 不抛异常")
    void of_withUnknownCode_returnsNull() {
        assertThat(OrderStatusEnum.of(null)).isNull();
        assertThat(OrderStatusEnum.of(99)).isNull();
        assertThat(PayStatusEnum.of(null)).isNull();
        assertThat(PayStatusEnum.of(9)).isNull();
        assertThat(OrderTypeEnum.of(null)).isNull();
        assertThat(OrderTypeEnum.of(3)).isNull();
    }

    @Test
    @DisplayName("OrderTypeEnum - 1商品 2服务")
    void orderType_codesMatchSchema() {
        assertThat(OrderTypeEnum.of(1)).isEqualTo(OrderTypeEnum.PRODUCT);
        assertThat(OrderTypeEnum.of(2)).isEqualTo(OrderTypeEnum.SERVICE);
    }
}
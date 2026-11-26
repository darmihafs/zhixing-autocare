package com.hongcheng.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态机（与 t_order.status 注释一致）。
 * <p>
 * 0待支付 → 10已支付 → 20服务中/已发货 → 30已完成；
 * 0待支付 → 40已取消（超时/用户）；
 * 10/20 → 50退款中 → 60已退款。
 * 状态流转校验在 order-service 实现，本枚举只定义状态与描述。
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    PENDING_PAYMENT(0, "待支付"),
    PAID(10, "已支付"),
    IN_SERVICE(20, "服务中/已发货"),
    COMPLETED(30, "已完成"),
    CANCELLED(40, "已取消"),
    REFUNDING(50, "退款中"),
    REFUNDED(60, "已退款");

    private final int code;
    private final String desc;

    /** 按 code 解析，未知 code 返回 null（调用方自行判空抛业务异常） */
    public static OrderStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
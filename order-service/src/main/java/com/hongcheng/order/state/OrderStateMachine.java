package com.hongcheng.order.state;

import com.hongcheng.common.enums.OrderStatusEnum;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;

import java.util.Map;
import java.util.Set;

/**
 * 订单状态机：只定义「允许流转」表，业务层在 CAS 更新前先校验。
 * <p>
 * 0待支付 → 10已支付 / 40已取消；
 * 10已支付 → 20服务中/已发货 / 50退款中；
 * 20服务中/已发货 → 30已完成 / 50退款中；
 * 50退款中 → 60已退款。
 * 任何不在表中的流转（如 已取消→已支付）一律拒绝。
 */
public final class OrderStateMachine {

    private static final Map<Integer, Set<Integer>> ALLOWED = Map.of(
            OrderStatusEnum.PENDING_PAYMENT.getCode(),
            Set.of(OrderStatusEnum.PAID.getCode(), OrderStatusEnum.CANCELLED.getCode()),
            OrderStatusEnum.PAID.getCode(),
            Set.of(OrderStatusEnum.IN_SERVICE.getCode(), OrderStatusEnum.REFUNDING.getCode()),
            OrderStatusEnum.IN_SERVICE.getCode(),
            Set.of(OrderStatusEnum.COMPLETED.getCode(), OrderStatusEnum.REFUNDING.getCode()),
            OrderStatusEnum.REFUNDING.getCode(),
            Set.of(OrderStatusEnum.REFUNDED.getCode())
    );

    private OrderStateMachine() {
    }

    public static boolean canTransit(Integer from, Integer to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** 校验流转合法性，非法抛 1302（订单状态不允许该操作） */
    public static void assertTransit(Integer from, Integer to) {
        if (!canTransit(from, to)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID,
                    "订单状态不允许从 " + from + " 流转到 " + to);
        }
    }
}

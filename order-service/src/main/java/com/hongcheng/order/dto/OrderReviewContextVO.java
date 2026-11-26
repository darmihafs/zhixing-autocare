package com.hongcheng.order.dto;

import com.hongcheng.common.enums.OrderStatusEnum;
import lombok.Data;

import java.util.List;

/**
 * 订单评价上下文（内部接口返回，供 review-service 校验评价资格）。
 * <p>
 * 只返回评价所需字段：归属用户、订单状态、门店与明细快照。
 */
@Data
public class OrderReviewContextVO {

    private Long orderId;

    private String orderNo;

    private Long userId;

    private Long shopId;

    private Integer orderType;

    /** 订单状态码，仅 30 已完成可评价 */
    private Integer status;

    private String statusDesc;

    private List<OrderReviewItemVO> items;

    public static OrderReviewContextVO of(Long orderId, String orderNo, Long userId, Long shopId,
                                          Integer orderType, Integer status, List<OrderReviewItemVO> items) {
        OrderReviewContextVO vo = new OrderReviewContextVO();
        vo.setOrderId(orderId);
        vo.setOrderNo(orderNo);
        vo.setUserId(userId);
        vo.setShopId(shopId);
        vo.setOrderType(orderType);
        vo.setStatus(status);
        OrderStatusEnum statusEnum = OrderStatusEnum.of(status);
        vo.setStatusDesc(statusEnum == null ? null : statusEnum.getDesc());
        vo.setItems(items);
        return vo;
    }
}

package com.hongcheng.review.dto;

import lombok.Data;

import java.util.List;

/**
 * 订单评价上下文（与 order-service OrderReviewContextVO 字段对齐）。
 */
@Data
public class OrderReviewContext {

    private Long orderId;

    private String orderNo;

    private Long userId;

    private Long shopId;

    private Integer orderType;

    private Integer status;

    private String statusDesc;

    private List<OrderReviewItem> items;
}

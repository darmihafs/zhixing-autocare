package com.hongcheng.review.dto;

import lombok.Data;

/**
 * 订单评价上下文中的明细项。
 */
@Data
public class OrderReviewItem {

    private Integer itemType;

    private Long itemId;

    private String itemName;

    private String itemImg;

    private Integer quantity;
}

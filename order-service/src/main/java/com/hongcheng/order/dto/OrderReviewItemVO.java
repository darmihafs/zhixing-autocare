package com.hongcheng.order.dto;

import com.hongcheng.order.entity.OrderItem;
import lombok.Data;

/**
 * 订单评价上下文中的明细项。
 */
@Data
public class OrderReviewItemVO {

    private Integer itemType;

    private Long itemId;

    private String itemName;

    private String itemImg;

    private Integer quantity;

    public static OrderReviewItemVO from(OrderItem item) {
        OrderReviewItemVO vo = new OrderReviewItemVO();
        vo.setItemType(item.getItemType());
        vo.setItemId(item.getItemId());
        vo.setItemName(item.getItemName());
        vo.setItemImg(item.getItemImg());
        vo.setQuantity(item.getQuantity());
        return vo;
    }
}

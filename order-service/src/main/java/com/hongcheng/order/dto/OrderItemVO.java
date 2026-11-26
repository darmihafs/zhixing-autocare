package com.hongcheng.order.dto;

import com.hongcheng.order.entity.OrderItem;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细视图。
 */
@Data
public class OrderItemVO {

    private Long itemId;

    private String itemName;

    private String itemImg;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal totalAmount;

    public static OrderItemVO from(OrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setItemId(item.getItemId());
        vo.setItemName(item.getItemName());
        vo.setItemImg(item.getItemImg());
        vo.setPrice(item.getPrice());
        vo.setQuantity(item.getQuantity());
        vo.setTotalAmount(item.getTotalAmount());
        return vo;
    }
}

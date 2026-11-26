package com.hongcheng.product.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车条目视图：价格/库存取商品当前值，失效商品标记 available=false。
 */
@Data
public class CartItemVO {

    private Long id;

    private Long productId;

    private String productName;

    private String coverImg;

    private BigDecimal price;

    private Integer stock;

    private Integer quantity;

    private Boolean selected;

    /** 商品是否仍可购买（上架且未删除） */
    private Boolean available;

    private BigDecimal totalAmount;
}

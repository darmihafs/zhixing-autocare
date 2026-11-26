package com.hongcheng.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车条目表 t_cart_item（用户 + 商品唯一）。
 */
@Data
@TableName("t_cart_item")
public class CartItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long productId;

    private Integer quantity;

    /** 1勾选 0未勾选 */
    private Integer selected;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

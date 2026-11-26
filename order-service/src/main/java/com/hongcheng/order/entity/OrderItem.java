package com.hongcheng.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细实体（t_order_item），单价与名称均为下单时快照。
 */
@Data
@TableName("t_order_item")
public class OrderItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long orderId;

    private String orderNo;

    /** 1商品 2服务项目 */
    private Integer itemType;

    /** 商品ID / 服务项目ID */
    private Long itemId;

    private String itemName;

    private String itemImg;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal totalAmount;

    private LocalDateTime createTime;
}

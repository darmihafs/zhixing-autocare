package com.hongcheng.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 下单明细行：商品订单填 productId，服务订单填 serviceItemId。
 */
@Data
public class OrderItemRequest {

    /** 商品ID（商品订单必填） */
    private Long productId;

    /** 服务项目ID（服务订单必填） */
    private Long serviceItemId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    @Max(value = 999, message = "单次购买数量不能超过999")
    private Integer quantity;
}

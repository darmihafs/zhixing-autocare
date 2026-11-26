package com.hongcheng.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 库存操作明细（扣减/回补共用）。
 */
@Data
public class StockItem {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量须为正整数")
    @Max(value = 999, message = "单次数量不能超过999")
    private Integer quantity;
}
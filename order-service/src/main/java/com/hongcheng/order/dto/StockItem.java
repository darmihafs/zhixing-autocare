package com.hongcheng.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存扣减/回补明细（与 product-service 内部接口约定一致）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockItem {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量须为正整数")
    @Max(value = 999, message = "单次数量不能超过999")
    private Integer quantity;
}

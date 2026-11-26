package com.hongcheng.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 修改购物车条目：数量与勾选状态均可选，至少传一个。
 */
@Data
public class CartUpdateRequest {

    @Min(value = 1, message = "数量至少为1")
    @Max(value = 999, message = "数量不能超过999")
    private Integer quantity;

    /** 1勾选 0未勾选 */
    private Integer selected;
}

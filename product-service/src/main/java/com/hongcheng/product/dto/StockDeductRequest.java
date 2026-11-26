package com.hongcheng.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 库存扣减/回补请求（整单事务：任一明细失败全部回滚）。
 */
@Data
public class StockDeductRequest {

    @NotEmpty(message = "库存明细不能为空")
    private List<@Valid StockItem> items;
}
package com.hongcheng.product.controller;

import com.hongcheng.common.result.Result;
import com.hongcheng.product.dto.StockDeductRequest;
import com.hongcheng.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部库存接口（仅供服务间调用，如 order-service）。
 * <p>
 * 网关不路由 /internal/** 前缀，外部流量到不了这里；
 * 直连调用须携带 X-Internal-Token（InternalTokenInterceptor 校验）。
 */
@RestController
@RequestMapping("/internal/product/stock")
@RequiredArgsConstructor
public class InternalStockController {

    private final ProductService productService;

    @PostMapping("/deduct")
    public Result<Void> deduct(@Valid @RequestBody StockDeductRequest request) {
        productService.deductStock(request);
        return Result.ok();
    }

    @PostMapping("/restore")
    public Result<Void> restore(@Valid @RequestBody StockDeductRequest request) {
        productService.restoreStock(request);
        return Result.ok();
    }
}
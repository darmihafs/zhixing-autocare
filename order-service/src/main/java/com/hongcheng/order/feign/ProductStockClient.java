package com.hongcheng.order.feign;

import com.hongcheng.common.result.Result;
import com.hongcheng.order.dto.StockDeductRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 商品服务内部库存接口（/internal/** 不在网关暴露；由 Feign 拦截器附带 X-Internal-Token）。
 */
@FeignClient(name = "product-service", contextId = "productStockClient", path = "/internal/product/stock",
        fallbackFactory = ProductStockClientFallbackFactory.class)
public interface ProductStockClient {

    @PostMapping("/deduct")
    Result<Void> deduct(@RequestBody StockDeductRequest request);

    @PostMapping("/restore")
    Result<Void> restore(@RequestBody StockDeductRequest request);
}

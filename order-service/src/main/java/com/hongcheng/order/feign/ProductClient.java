package com.hongcheng.order.feign;

import com.hongcheng.common.result.Result;
import com.hongcheng.order.dto.ProductInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 商品服务公开查询客户端：下单时获取名称/价格快照与上下架状态。
 */
@FeignClient(name = "product-service", contextId = "productClient", path = "/product",
        fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {

    @GetMapping("/{id}")
    Result<ProductInfo> detail(@PathVariable("id") Long id);
}

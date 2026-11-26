package com.hongcheng.ai.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.hongcheng.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 工具 search_products 的数据源：商品服务真实在售商品分页检索。
 */
@FeignClient(name = "product-service", contextId = "aiProductClient", path = "/product",
        fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {

    @GetMapping("/page")
    Result<JsonNode> page(@RequestParam("keyword") String keyword,
                          @RequestParam("pageNum") int pageNum,
                          @RequestParam("pageSize") int pageSize);
}
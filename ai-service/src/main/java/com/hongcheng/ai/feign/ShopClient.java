package com.hongcheng.ai.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.hongcheng.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 工具 search_shops_and_services 的数据源：门店服务 ES(IK) 分词检索。
 */
@FeignClient(name = "shop-service", contextId = "aiShopClient", path = "/shop",
        fallbackFactory = ShopClientFallbackFactory.class)
public interface ShopClient {

    @GetMapping("/search")
    Result<JsonNode> searchShops(@RequestParam("keyword") String keyword,
                                 @RequestParam("pageNum") int pageNum,
                                 @RequestParam("pageSize") int pageSize);

    @GetMapping("/service/search")
    Result<JsonNode> searchServices(@RequestParam("keyword") String keyword,
                                    @RequestParam("pageNum") int pageNum,
                                    @RequestParam("pageSize") int pageSize);
}
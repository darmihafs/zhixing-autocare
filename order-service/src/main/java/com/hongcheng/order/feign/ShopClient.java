package com.hongcheng.order.feign;

import com.hongcheng.common.result.Result;
import com.hongcheng.order.dto.ServiceItemInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 门店服务项目内部查询客户端：下单时获取服务名称/价格快照与上下架状态。
 * <p>
 * /internal/** 由网关屏蔽，Feign 全局拦截器会自动附带 X-Internal-Token。
 */
@FeignClient(name = "shop-service", contextId = "shopClient", path = "/internal/shop/service",
        fallbackFactory = ShopClientFallbackFactory.class)
public interface ShopClient {

    @GetMapping("/{id}")
    Result<ServiceItemInfo> detail(@PathVariable("id") Long id);
}

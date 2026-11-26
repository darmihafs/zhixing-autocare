package com.hongcheng.shop.controller;

import com.hongcheng.common.result.Result;
import com.hongcheng.shop.dto.ServiceItemVO;
import com.hongcheng.shop.service.ServiceItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部服务项目查询：供 order-service 下单时获取名称/价格快照与上下架状态。
 * 网关不路由 /internal/**，需携带 X-Internal-Token。
 */
@RestController
@RequestMapping("/internal/shop/service")
@RequiredArgsConstructor
public class InternalServiceItemController {

    private final ServiceItemService serviceItemService;

    @GetMapping("/{id}")
    public Result<ServiceItemVO> detail(@PathVariable Long id) {
        return Result.ok(serviceItemService.detailForOrder(id));
    }
}

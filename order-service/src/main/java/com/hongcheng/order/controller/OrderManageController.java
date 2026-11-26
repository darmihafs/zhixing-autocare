package com.hongcheng.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongcheng.common.result.Result;
import com.hongcheng.order.dto.OrderAdminVO;
import com.hongcheng.order.service.OrderService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单管理员接口（/order/manage/** 由 AdminRoleInterceptor 校验 X-User-Role == ADMIN）。
 * <p>
 * 接单/发货复用用户侧同一状态机（10→20）与 WebSocket 进度推送，保证 C 端体验不变。
 */
@Validated
@RestController
@RequestMapping("/order/manage")
@RequiredArgsConstructor
public class OrderManageController {

    private final OrderService orderService;

    /** 管理员订单分页（可按状态/类型过滤，默认看待处理）；含真实用户名/昵称，敏感字段部分脱敏 */
    @GetMapping("/page")
    public Result<Page<OrderAdminVO>> page(@RequestParam(required = false) Integer status,
                                      @RequestParam(required = false) Integer orderType,
                                      @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
                                      @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer pageSize) {
        return Result.ok(orderService.adminPage(status, orderType, pageNum, pageSize));
    }

    /** 门店接单（仅养车服务订单） */
    @PostMapping("/{orderNo}/accept")
    public Result<Void> accept(@PathVariable String orderNo) {
        orderService.adminAccept(orderNo);
        return Result.ok();
    }

    /** 商家发货（仅商品订单） */
    @PostMapping("/{orderNo}/deliver")
    public Result<Void> deliver(@PathVariable String orderNo) {
        orderService.adminDeliver(orderNo);
        return Result.ok();
    }
}
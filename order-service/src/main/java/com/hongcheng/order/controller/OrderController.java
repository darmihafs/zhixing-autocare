package com.hongcheng.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.order.dto.CreateOrderRequest;
import com.hongcheng.order.dto.OrderCreateStateVO;
import com.hongcheng.order.dto.OrderVO;
import com.hongcheng.order.service.OrderService;
import com.hongcheng.order.service.OrderSubmitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单用户接口（网关 JWT 鉴权后透传 X-User-Id）。
 * <p>
 * 下单为异步削峰：POST /order 只返回 orderNo 与 QUEUED，客户端轮询
 * GET /order/state/{orderNo} 或直接查详情获得最终结果。
 */
@Validated
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderSubmitService orderSubmitService;
    private final OrderService orderService;

    @Value("${hc.demo.enabled:true}")
    private boolean demoEnabled = true;

    /** 提交下单请求（入队即返回） */
    @PostMapping
    public Result<OrderCreateStateVO> create(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                             @Valid @RequestBody CreateOrderRequest request) {
        return Result.ok(orderSubmitService.submit(userId, request));
    }

    /** 下单请求处理状态（仅本人或管理员可查：QUEUED / CREATED / FAILED / NOT_FOUND） */
    @GetMapping("/state/{orderNo}")
    public Result<OrderCreateStateVO> state(
            @RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
            @RequestHeader(value = CommonConstants.HEADER_USER_ROLE, required = false) String role,
            @PathVariable String orderNo) {
        return Result.ok(orderService.state(orderNo, userId, CommonConstants.ROLE_ADMIN.equals(role)));
    }

    /** 我的订单分页 */
    @GetMapping("/page")
    public Result<Page<OrderVO>> page(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                      @RequestParam(required = false) Integer status,
                                      @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
                                      @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer pageSize) {
        return Result.ok(orderService.page(userId, status, pageNum, pageSize));
    }

    /** 订单详情（仅本人） */
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                  @PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo, userId));
    }

    /** 取消订单（仅待支付） */
    @PutMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                               @PathVariable String orderNo) {
        orderService.cancel(orderNo, userId);
        return Result.ok();
    }

    /** 确认完成（仅服务中/已发货） */
    @PutMapping("/{orderNo}/complete")
    public Result<Void> complete(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                 @PathVariable String orderNo) {
        orderService.complete(orderNo, userId);
        return Result.ok();
    }

    /**
     * 演示用：订单本人触发“商家发货”，已支付(10) → 服务中(20)。
     * <p>
     * 受 hc.demo.enabled 开关控制，生产通过 HC_DEMO_ENABLED=false 一键关闭。
     */
    @PostMapping("/{orderNo}/demo-deliver")
    public Result<Void> demoDeliver(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                    @PathVariable String orderNo) {
        if (!demoEnabled) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前环境未开启演示发货接口");
        }
        orderService.demoDeliver(orderNo, userId);
        return Result.ok();
    }
}

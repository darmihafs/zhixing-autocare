package com.hongcheng.order.controller;

import com.hongcheng.common.result.Result;
import com.hongcheng.order.dto.OrderPayInfoVO;
import com.hongcheng.order.dto.OrderReviewContextVO;
import com.hongcheng.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单内部接口（供 pay-service 回调；/internal/** 需 X-Internal-Token，网关不路由）。
 */
@RestController
@RequestMapping("/internal/order")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    /** 支付服务创建支付单前查询订单归属、应付金额与状态 */
    @GetMapping("/{orderNo}")
    public Result<OrderPayInfoVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.payInfo(orderNo));
    }

    /** 评价服务创建评价前查询订单归属、状态与明细快照 */
    @GetMapping("/{orderNo}/review-context")
    public Result<OrderReviewContextVO> reviewContext(@PathVariable String orderNo) {
        return Result.ok(orderService.reviewContext(orderNo));
    }

    /** 支付成功回调：0待支付 → 10已支付（重复回调幂等） */
    @PostMapping("/{orderNo}/pay")
    public Result<Void> pay(@PathVariable String orderNo) {
        orderService.markPaid(orderNo);
        return Result.ok();
    }

    /** 发货/进入服务：10已支付 → 20服务中/已发货（重复调用幂等） */
    @PostMapping("/{orderNo}/deliver")
    public Result<Void> deliver(@PathVariable String orderNo) {
        orderService.markDelivered(orderNo);
        return Result.ok();
    }
}

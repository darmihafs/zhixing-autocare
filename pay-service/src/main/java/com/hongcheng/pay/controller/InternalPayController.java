package com.hongcheng.pay.controller;

import com.hongcheng.common.result.Result;
import com.hongcheng.pay.dto.PayCallbackRequest;
import com.hongcheng.pay.dto.PayCallbackResultVO;
import com.hongcheng.pay.service.PayCallbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第三方支付回调入口（/internal/** 需 X-Internal-Token，网关不路由）。
 */
@RestController
@RequestMapping("/internal/pay")
@RequiredArgsConstructor
public class InternalPayController {

    private final PayCallbackService payCallbackService;

    /** 支付结果回调：以 out_trade_no 幂等，成功时联动订单状态机 0→10 */
    @PostMapping("/callback")
    public Result<PayCallbackResultVO> callback(@Valid @RequestBody PayCallbackRequest request) {
        return Result.ok(payCallbackService.handle(request));
    }
}

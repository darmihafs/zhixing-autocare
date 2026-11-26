package com.hongcheng.pay.controller;

import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.pay.dto.CreatePayRequest;
import com.hongcheng.pay.dto.PayTransactionVO;
import com.hongcheng.pay.service.PayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付用户接口（经网关 JWT，X-User-Id 由网关注入）。
 */
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    @Value("${hc.demo.enabled:true}")
    private boolean demoEnabled = true;

    /** 创建支付单（金额取订单应付金额，一单仅复用支付中流水） */
    @PostMapping("/create")
    public Result<PayTransactionVO> create(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                           @Valid @RequestBody CreatePayRequest request) {
        return Result.ok(payService.create(userId, request.getOrderNo()));
    }

    /** 模拟支付成功（受 hc.demo.enabled 控制，生产通过 HC_DEMO_ENABLED=false 关闭） */
    @PostMapping("/{outTradeNo}/mock")
    public Result<PayTransactionVO> mockPay(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                            @PathVariable String outTradeNo) {
        if (!demoEnabled) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前环境未开启模拟支付接口");
        }
        return Result.ok(payService.mockPay(userId, outTradeNo));
    }

    /** 查询本人支付单状态（WebSocket 不在线时用于轮询兜底） */
    @GetMapping("/{outTradeNo}")
    public Result<PayTransactionVO> detail(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                           @PathVariable String outTradeNo) {
        return Result.ok(payService.detail(userId, outTradeNo));
    }
}

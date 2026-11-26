package com.hongcheng.pay.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hongcheng.common.enums.OrderStatusEnum;
import com.hongcheng.common.enums.PayStatusEnum;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.pay.dto.OrderPayInfo;
import com.hongcheng.pay.dto.PayCallbackRequest;
import com.hongcheng.pay.dto.PayTransactionVO;
import com.hongcheng.pay.entity.PayTransaction;
import com.hongcheng.pay.feign.OrderPayClient;
import com.hongcheng.pay.mapper.PayTransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 支付单服务：创建、mock 支付、查询。
 * <p>
 * 创建前通过订单服务内部接口校验订单归属与应付金额，金额不信任客户端；
 * 同一订单已有支付中流水时复用，避免重复发起。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {

    private static final String CHANNEL_MOCK = "MOCK";

    private final PayTransactionMapper payTransactionMapper;
    private final OrderPayClient orderPayClient;
    private final PayCallbackService payCallbackService;

    @SentinelResource(value = "payCreate", blockHandler = "createBlocked")
    public PayTransactionVO create(Long userId, String orderNo) {
        Result<OrderPayInfo> orderResult;
        try {
            orderResult = orderPayClient.detail(orderNo);
        } catch (Exception e) {
            log.error("查询订单失败 orderNo={}", orderNo, e);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "订单服务暂不可用");
        }
        if (orderResult == null || !orderResult.isSuccess() || orderResult.getData() == null) {
            String message = orderResult == null ? "订单服务无响应" : orderResult.getMessage();
            if (orderResult == null || orderResult.getCode() == ResultCode.SERVICE_UNAVAILABLE.getCode()) {
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "订单服务暂不可用");
            }
            throw new BusinessException(ResultCode.PAY_ORDER_NOT_PAYABLE, "订单不可支付：" + message);
        }
        OrderPayInfo order = orderResult.getData();
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PAY_ACCESS_DENIED);
        }
        if (!Integer.valueOf(OrderStatusEnum.PENDING_PAYMENT.getCode()).equals(order.getStatus())) {
            throw new BusinessException(ResultCode.PAY_ORDER_NOT_PAYABLE,
                    "订单状态为「" + statusDesc(order.getStatus()) + "」，不可支付");
        }
        PayTransaction open = payTransactionMapper.selectOpenByOrderNo(orderNo);
        if (open != null) {
            log.info("复用支付中流水 orderNo={}, outTradeNo={}", orderNo, open.getOutTradeNo());
            return PayTransactionVO.from(open);
        }

        PayTransaction transaction = new PayTransaction();
        transaction.setId(IdWorker.getId());
        transaction.setOutTradeNo("PAY" + transaction.getId());
        transaction.setOrderNo(orderNo);
        transaction.setUserId(userId);
        transaction.setAmount(order.getPayAmount());
        transaction.setChannel(CHANNEL_MOCK);
        transaction.setStatus(PayStatusEnum.PAYING.getCode());
        payTransactionMapper.insert(transaction);
        log.info("支付单创建成功 outTradeNo={}, orderNo={}, userId={}, amount={}",
                transaction.getOutTradeNo(), orderNo, userId, order.getPayAmount());
        return PayTransactionVO.from(transaction);
    }

    /** 创建支付单被 Sentinel 流控/熔断拦截时的兜底 */
    public PayTransactionVO createBlocked(Long userId, String orderNo, BlockException e) {
        log.warn("创建支付单被 Sentinel 拦截 userId={}, orderNo={}, blockType={}",
                userId, orderNo, e.getClass().getSimpleName());
        throw new BusinessException(ResultCode.PAY_RATE_LIMITED);
    }

    /** 模拟第三方支付成功：走与真实回调相同的幂等处理链路 */
    public PayTransactionVO mockPay(Long userId, String outTradeNo) {
        PayTransaction transaction = requireOwned(outTradeNo, userId);
        PayCallbackRequest callback = new PayCallbackRequest();
        callback.setOutTradeNo(outTradeNo);
        callback.setThirdTradeNo("MOCK" + System.currentTimeMillis());
        callback.setSuccess(true);
        callback.setAmount(transaction.getAmount());
        boolean idempotent = payCallbackService.handle(callback).isIdempotent();
        log.info("模拟支付完成 outTradeNo={}, userId={}, idempotent={}", outTradeNo, userId, idempotent);
        return PayTransactionVO.from(requireOwned(outTradeNo, userId));
    }

    public PayTransactionVO detail(Long userId, String outTradeNo) {
        return PayTransactionVO.from(requireOwned(outTradeNo, userId));
    }

    private PayTransaction requireOwned(String outTradeNo, Long userId) {
        PayTransaction transaction = payTransactionMapper.selectByOutTradeNo(outTradeNo);
        if (transaction == null) {
            throw new BusinessException(ResultCode.PAY_NOT_FOUND);
        }
        if (!transaction.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.PAY_ACCESS_DENIED);
        }
        return transaction;
    }

    private String statusDesc(Integer status) {
        OrderStatusEnum orderStatus = OrderStatusEnum.of(status);
        return orderStatus == null ? String.valueOf(status) : orderStatus.getDesc();
    }
}

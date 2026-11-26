package com.hongcheng.pay.service;

import com.hongcheng.common.enums.PayStatusEnum;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.pay.dto.PayCallbackRequest;
import com.hongcheng.pay.dto.PayCallbackResultVO;
import com.hongcheng.pay.dto.PayNotifyMessage;
import com.hongcheng.pay.entity.PayTransaction;
import com.hongcheng.pay.feign.OrderPayClient;
import com.hongcheng.pay.mapper.PayTransactionMapper;
import com.hongcheng.pay.websocket.PayWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 支付回调核心：幂等 + 订单状态机联动 + WebSocket 推送。
 * <p>
 * 幂等设计：
 * 1. out_trade_no 是回调唯一标识，重复回调直接返回成功语义；
 * 2. 状态变更用条件 UPDATE（status=0）保证并发回调只有一方产生业务效果；
 * 3. 先回调订单服务（其 0→10 本身幂等），成功后再 CAS 支付单 0→1；
 *    若支付单 CAS 失败而订单已支付，重复回调会再次幂等调用订单后补写状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayCallbackService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PayTransactionMapper payTransactionMapper;
    private final OrderPayClient orderPayClient;
    private final PayWebSocketHandler payWebSocketHandler;

    public PayCallbackResultVO handle(PayCallbackRequest request) {
        PayTransaction transaction = payTransactionMapper.selectByOutTradeNo(request.getOutTradeNo());
        if (transaction == null) {
            throw new BusinessException(ResultCode.PAY_NOT_FOUND);
        }
        if (transaction.getAmount().compareTo(request.getAmount()) != 0) {
            log.warn("回调金额不一致 outTradeNo={}, expect={}, actual={}",
                    request.getOutTradeNo(), transaction.getAmount(), request.getAmount());
            throw new BusinessException(ResultCode.PAY_AMOUNT_MISMATCH);
        }
        if (PayStatusEnum.SUCCESS.getCode() == transaction.getStatus()) {
            log.info("支付单已成功，重复回调幂等返回 outTradeNo={}", request.getOutTradeNo());
            return idempotent(transaction);
        }
        if (PayStatusEnum.FAILED.getCode() == transaction.getStatus()
                && !Boolean.TRUE.equals(request.getSuccess())) {
            log.info("支付单已失败，重复失败回调幂等返回 outTradeNo={}", request.getOutTradeNo());
            return idempotent(transaction);
        }
        if (PayStatusEnum.PAYING.getCode() != transaction.getStatus()) {
            throw new BusinessException(ResultCode.PAY_STATUS_INVALID);
        }
        return Boolean.TRUE.equals(request.getSuccess())
                ? handleSuccess(transaction, request)
                : handleFailure(transaction, request);
    }

    private PayCallbackResultVO handleSuccess(PayTransaction transaction, PayCallbackRequest request) {
        // 先推进订单状态机（订单侧 0→10 幂等），成功后再落支付单成功
        Result<Void> orderResult;
        try {
            orderResult = orderPayClient.pay(transaction.getOrderNo());
        } catch (Exception e) {
            log.error("调用订单服务支付回调失败 orderNo={}, outTradeNo={}",
                    transaction.getOrderNo(), transaction.getOutTradeNo(), e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "订单服务回调失败，请稍后重试");
        }
        if (orderResult == null || !orderResult.isSuccess()) {
            int code = orderResult == null ? ResultCode.SERVICE_UNAVAILABLE.getCode() : orderResult.getCode();
            String message = orderResult == null ? "订单服务无响应" : orderResult.getMessage();
            if (code == ResultCode.SERVICE_UNAVAILABLE.getCode()) {
                // 下游不可用属于可重试故障：不得把支付单置为失败，等第三方重试回调
                log.warn("订单服务不可用，支付回调暂不落账 outTradeNo={}, orderNo={}",
                        transaction.getOutTradeNo(), transaction.getOrderNo());
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "订单服务暂不可用，请稍后重试");
            }
            PayTransaction failed = markFailed(transaction, request.getThirdTradeNo(), message);
            pushFailed(failed, message);
            log.warn("订单不可支付，支付单置为失败 outTradeNo={}, orderNo={}, orderCode={}",
                    transaction.getOutTradeNo(), transaction.getOrderNo(), code);
            throw new BusinessException(ResultCode.PAY_ORDER_NOT_PAYABLE, "订单不可支付：" + message);
        }

        LocalDateTime now = LocalDateTime.now();
        int rows = payTransactionMapper.markSuccess(transaction.getId(), request.getThirdTradeNo(), now, now);
        if (rows == 0) {
            PayTransaction latest = payTransactionMapper.selectByOutTradeNo(transaction.getOutTradeNo());
            if (latest != null && PayStatusEnum.SUCCESS.getCode() == latest.getStatus()) {
                log.info("支付成功并发回调，后到请求幂等返回 outTradeNo={}", transaction.getOutTradeNo());
                return idempotent(latest);
            }
            throw new BusinessException(ResultCode.PAY_STATUS_INVALID, "支付单状态已变更");
        }
        PayTransaction paid = payTransactionMapper.selectByOutTradeNo(transaction.getOutTradeNo());
        pushSuccess(paid);
        log.info("支付成功 outTradeNo={}, orderNo={}, amount={}",
                paid.getOutTradeNo(), paid.getOrderNo(), paid.getAmount());
        return result(paid, false);
    }

    private PayCallbackResultVO handleFailure(PayTransaction transaction, PayCallbackRequest request) {
        PayTransaction failed = markFailed(transaction, request.getThirdTradeNo(), "第三方返回支付失败");
        pushFailed(failed, "支付失败");
        log.info("支付失败回调 outTradeNo={}", failed.getOutTradeNo());
        return result(failed, false);
    }

    private PayTransaction markFailed(PayTransaction transaction, String thirdTradeNo, String message) {
        int rows = payTransactionMapper.markFailed(transaction.getId(), thirdTradeNo, LocalDateTime.now());
        PayTransaction latest = payTransactionMapper.selectByOutTradeNo(transaction.getOutTradeNo());
        if (rows == 0 && latest != null && PayStatusEnum.SUCCESS.getCode() == latest.getStatus()) {
            // 并发竞争：另一回调已成功，不能把成功单覆盖为失败
            throw new BusinessException(ResultCode.PAY_STATUS_INVALID, "支付单已支付成功，忽略失败回调");
        }
        log.info("支付单置为失败 outTradeNo={}, reason={}", transaction.getOutTradeNo(), message);
        return latest == null ? transaction : latest;
    }

    private void pushSuccess(PayTransaction transaction) {
        payWebSocketHandler.push(transaction.getUserId(), new PayNotifyMessage(
                "PAY_SUCCESS", transaction.getOutTradeNo(), transaction.getOrderNo(),
                transaction.getAmount(), PayStatusEnum.SUCCESS.getCode(),
                transaction.getPayTime() == null ? null : transaction.getPayTime().format(TIME_FORMATTER),
                "支付成功"));
    }

    private void pushFailed(PayTransaction transaction, String message) {
        payWebSocketHandler.push(transaction.getUserId(), new PayNotifyMessage(
                "PAY_FAILED", transaction.getOutTradeNo(), transaction.getOrderNo(),
                transaction.getAmount(), PayStatusEnum.FAILED.getCode(), null, message));
    }

    private PayCallbackResultVO idempotent(PayTransaction transaction) {
        return result(transaction, true);
    }

    private PayCallbackResultVO result(PayTransaction transaction, boolean idempotent) {
        PayStatusEnum status = PayStatusEnum.of(transaction.getStatus());
        return new PayCallbackResultVO(transaction.getOutTradeNo(), transaction.getStatus(),
                status == null ? null : status.getDesc(), idempotent);
    }
}

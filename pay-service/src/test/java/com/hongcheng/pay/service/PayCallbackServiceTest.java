package com.hongcheng.pay.service;

import com.hongcheng.common.enums.PayStatusEnum;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.pay.dto.PayCallbackRequest;
import com.hongcheng.pay.dto.PayCallbackResultVO;
import com.hongcheng.pay.entity.PayTransaction;
import com.hongcheng.pay.feign.OrderPayClient;
import com.hongcheng.pay.mapper.PayTransactionMapper;
import com.hongcheng.pay.websocket.PayWebSocketHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 支付回调服务测试：金额校验、状态 CAS、订单联动、重复回调幂等、失败回调。
 */
@ExtendWith(MockitoExtension.class)
class PayCallbackServiceTest {

    private static final String OUT_TRADE_NO = "PAY900000000000000001";
    private static final String ORDER_NO = "HC900000000000000002";
    private static final Long TX_ID = 900000000000000001L;
    private static final Long USER_ID = 1L;

    @Mock
    private PayTransactionMapper payTransactionMapper;

    @Mock
    private OrderPayClient orderPayClient;

    @Mock
    private PayWebSocketHandler payWebSocketHandler;

    @InjectMocks
    private PayCallbackService payCallbackService;

    @Test
    @DisplayName("支付成功回调 - 订单 0→10 后支付单 CAS 成功并推送一次")
    void handle_whenSuccess_updatesOrderAndPushesOnce() {
        when(payTransactionMapper.selectByOutTradeNo(OUT_TRADE_NO))
                .thenReturn(transaction(PayStatusEnum.PAYING.getCode()))
                .thenReturn(transaction(PayStatusEnum.SUCCESS.getCode()));
        when(orderPayClient.pay(ORDER_NO)).thenReturn(Result.ok());
        when(payTransactionMapper.markSuccess(eq(TX_ID), eq("MOCK-T1"), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(1);

        PayCallbackResultVO result = payCallbackService.handle(successCallback());

        assertThat(result.getStatus()).isEqualTo(PayStatusEnum.SUCCESS.getCode());
        assertThat(result.isIdempotent()).isFalse();
        verify(orderPayClient).pay(ORDER_NO);
        verify(payWebSocketHandler).push(eq(USER_ID), any());
    }

    @Test
    @DisplayName("重复成功回调 - 直接幂等返回，不再调用订单/推送")
    void handle_whenAlreadySuccess_returnsIdempotent() {
        when(payTransactionMapper.selectByOutTradeNo(OUT_TRADE_NO))
                .thenReturn(transaction(PayStatusEnum.SUCCESS.getCode()));

        PayCallbackResultVO result = payCallbackService.handle(successCallback());

        assertThat(result.isIdempotent()).isTrue();
        assertThat(result.getStatus()).isEqualTo(PayStatusEnum.SUCCESS.getCode());
        verifyNoInteractions(orderPayClient, payWebSocketHandler);
    }

    @Test
    @DisplayName("回调支付单不存在 - 抛 1401")
    void handle_whenNotFound_throwsPayNotFound() {
        when(payTransactionMapper.selectByOutTradeNo(OUT_TRADE_NO)).thenReturn(null);

        assertThatThrownBy(() -> payCallbackService.handle(successCallback()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1401));
    }

    @Test
    @DisplayName("回调金额不一致 - 抛 1403 且不改状态")
    void handle_whenAmountMismatch_throwsAmountMismatch() {
        when(payTransactionMapper.selectByOutTradeNo(OUT_TRADE_NO))
                .thenReturn(transaction(PayStatusEnum.PAYING.getCode()));
        PayCallbackRequest request = successCallback();
        request.setAmount(new BigDecimal("0.01"));

        assertThatThrownBy(() -> payCallbackService.handle(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1403));
        verify(payTransactionMapper, never()).markSuccess(anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("订单不可支付 - 支付单置失败并抛 1404")
    void handle_whenOrderNotPayable_marksFailedAndThrows() {
        when(payTransactionMapper.selectByOutTradeNo(OUT_TRADE_NO))
                .thenReturn(transaction(PayStatusEnum.PAYING.getCode()))
                .thenReturn(transaction(PayStatusEnum.FAILED.getCode()));
        when(orderPayClient.pay(ORDER_NO)).thenReturn(Result.fail(ResultCode.ORDER_STATUS_INVALID));
        when(payTransactionMapper.markFailed(eq(TX_ID), anyString(), any(LocalDateTime.class))).thenReturn(1);

        assertThatThrownBy(() -> payCallbackService.handle(successCallback()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1404));
        verify(payTransactionMapper).markFailed(eq(TX_ID), eq("MOCK-T1"), any(LocalDateTime.class));
        verify(payTransactionMapper, never()).markSuccess(anyLong(), anyString(), any(), any());
        verify(payWebSocketHandler).push(eq(USER_ID), any());
    }

    @Test
    @DisplayName("并发成功回调 CAS 未命中 - 后到请求幂等返回且不重复推送")
    void handle_whenCasMissesAfterConcurrentSuccess_returnsIdempotent() {
        when(payTransactionMapper.selectByOutTradeNo(OUT_TRADE_NO))
                .thenReturn(transaction(PayStatusEnum.PAYING.getCode()))
                .thenReturn(transaction(PayStatusEnum.SUCCESS.getCode()));
        when(orderPayClient.pay(ORDER_NO)).thenReturn(Result.ok());
        when(payTransactionMapper.markSuccess(eq(TX_ID), anyString(), any(), any())).thenReturn(0);

        PayCallbackResultVO result = payCallbackService.handle(successCallback());

        assertThat(result.isIdempotent()).isTrue();
        verify(payWebSocketHandler, never()).push(anyLong(), any());
    }

    @Test
    @DisplayName("第三方失败回调 - 支付单 0→2 并推送失败结果")
    void handle_whenThirdPartyFails_marksFailedAndPushes() {
        when(payTransactionMapper.selectByOutTradeNo(OUT_TRADE_NO))
                .thenReturn(transaction(PayStatusEnum.PAYING.getCode()))
                .thenReturn(transaction(PayStatusEnum.FAILED.getCode()));
        when(payTransactionMapper.markFailed(eq(TX_ID), anyString(), any(LocalDateTime.class))).thenReturn(1);
        PayCallbackRequest request = successCallback();
        request.setSuccess(false);

        PayCallbackResultVO result = payCallbackService.handle(request);

        assertThat(result.getStatus()).isEqualTo(PayStatusEnum.FAILED.getCode());
        assertThat(result.isIdempotent()).isFalse();
        verifyNoInteractions(orderPayClient);
        ArgumentCaptor<com.hongcheng.pay.dto.PayNotifyMessage> captor =
                ArgumentCaptor.forClass(com.hongcheng.pay.dto.PayNotifyMessage.class);
        verify(payWebSocketHandler).push(eq(USER_ID), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("PAY_FAILED");
    }

    @Test
    @DisplayName("支付单已关闭 - 再次成功回调抛 1402")
    void handle_whenClosed_throwsStatusInvalid() {
        when(payTransactionMapper.selectByOutTradeNo(OUT_TRADE_NO))
                .thenReturn(transaction(PayStatusEnum.CLOSED.getCode()));

        assertThatThrownBy(() -> payCallbackService.handle(successCallback()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1402));
    }

    private PayCallbackRequest successCallback() {
        PayCallbackRequest request = new PayCallbackRequest();
        request.setOutTradeNo(OUT_TRADE_NO);
        request.setThirdTradeNo("MOCK-T1");
        request.setSuccess(true);
        request.setAmount(new BigDecimal("99.90"));
        return request;
    }

    private PayTransaction transaction(int status) {
        PayTransaction transaction = new PayTransaction();
        transaction.setId(TX_ID);
        transaction.setOutTradeNo(OUT_TRADE_NO);
        transaction.setOrderNo(ORDER_NO);
        transaction.setUserId(USER_ID);
        transaction.setAmount(new BigDecimal("99.90"));
        transaction.setStatus(status);
        transaction.setPayTime(status == PayStatusEnum.SUCCESS.getCode() ? LocalDateTime.now() : null);
        return transaction;
    }
}

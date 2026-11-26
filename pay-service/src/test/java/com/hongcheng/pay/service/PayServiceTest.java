package com.hongcheng.pay.service;

import com.hongcheng.common.enums.OrderStatusEnum;
import com.hongcheng.common.enums.PayStatusEnum;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.pay.dto.OrderPayInfo;
import com.hongcheng.pay.dto.PayCallbackResultVO;
import com.hongcheng.pay.dto.PayTransactionVO;
import com.hongcheng.pay.entity.PayTransaction;
import com.hongcheng.pay.feign.OrderPayClient;
import com.hongcheng.pay.mapper.PayTransactionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付单服务测试：归属校验、状态校验、支付中流水复用、mock 支付走幂等回调。
 */
@ExtendWith(MockitoExtension.class)
class PayServiceTest {

    private static final String OUT_TRADE_NO = "PAY900000000000000001";
    private static final String ORDER_NO = "HC900000000000000002";
    private static final Long USER_ID = 1L;

    @Mock
    private PayTransactionMapper payTransactionMapper;

    @Mock
    private OrderPayClient orderPayClient;

    @Mock
    private PayCallbackService payCallbackService;

    @InjectMocks
    private PayService payService;

    @Test
    @DisplayName("创建支付单 - 金额取订单应付金额并落库支付中流水")
    void create_whenPayable_insertsPayingTransaction() {
        when(orderPayClient.detail(ORDER_NO)).thenReturn(Result.ok(order(USER_ID, 0, "99.90")));
        when(payTransactionMapper.selectOpenByOrderNo(ORDER_NO)).thenReturn(null);

        PayTransactionVO vo = payService.create(USER_ID, ORDER_NO);

        assertThat(vo.getOutTradeNo()).startsWith("PAY");
        assertThat(vo.getAmount()).isEqualByComparingTo("99.90");
        assertThat(vo.getStatus()).isEqualTo(PayStatusEnum.PAYING.getCode());
        assertThat(vo.getChannel()).isEqualTo("MOCK");
        ArgumentCaptor<PayTransaction> captor = ArgumentCaptor.forClass(PayTransaction.class);
        verify(payTransactionMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
    }

    @Test
    @DisplayName("创建支付单 - 已有支付中流水时复用，不重复插入")
    void create_whenOpenTransactionExists_reusesIt() {
        when(orderPayClient.detail(ORDER_NO)).thenReturn(Result.ok(order(USER_ID, 0, "99.90")));
        PayTransaction open = transaction(USER_ID, PayStatusEnum.PAYING.getCode());
        when(payTransactionMapper.selectOpenByOrderNo(ORDER_NO)).thenReturn(open);

        PayTransactionVO vo = payService.create(USER_ID, ORDER_NO);

        assertThat(vo.getOutTradeNo()).isEqualTo(OUT_TRADE_NO);
        verify(payTransactionMapper, never()).insert(any(PayTransaction.class));
    }

    @Test
    @DisplayName("创建支付单 - 非订单本人抛 1405")
    void create_whenNotOwner_throwsAccessDenied() {
        when(orderPayClient.detail(ORDER_NO)).thenReturn(Result.ok(order(2L, 0, "99.90")));

        assertThatThrownBy(() -> payService.create(USER_ID, ORDER_NO))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1405));
        verify(payTransactionMapper, never()).insert(any(PayTransaction.class));
    }

    @Test
    @DisplayName("创建支付单 - 订单已支付抛 1404")
    void create_whenOrderNotPending_throwsNotPayable() {
        when(orderPayClient.detail(ORDER_NO))
                .thenReturn(Result.ok(order(USER_ID, OrderStatusEnum.PAID.getCode(), "99.90")));

        assertThatThrownBy(() -> payService.create(USER_ID, ORDER_NO))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1404));
    }

    @Test
    @DisplayName("创建支付单 - 订单不存在时抛 1404 且带订单侧原因")
    void create_whenOrderMissing_throwsNotPayable() {
        when(orderPayClient.detail(ORDER_NO)).thenReturn(Result.fail(ResultCode.ORDER_NOT_FOUND));

        assertThatThrownBy(() -> payService.create(USER_ID, ORDER_NO))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getCode()).isEqualTo(1404);
                    assertThat(e.getMessage()).contains("订单不存在");
                });
    }

    @Test
    @DisplayName("模拟支付 - 以支付单金额构造回调并返回最新状态")
    void mockPay_whenOwner_delegatesToIdempotentCallback() {
        PayTransaction paying = transaction(USER_ID, PayStatusEnum.PAYING.getCode());
        PayTransaction paid = transaction(USER_ID, PayStatusEnum.SUCCESS.getCode());
        when(payTransactionMapper.selectByOutTradeNo(OUT_TRADE_NO)).thenReturn(paying, paid);
        when(payCallbackService.handle(any())).thenReturn(
                new PayCallbackResultVO(OUT_TRADE_NO, PayStatusEnum.SUCCESS.getCode(), "支付成功", false));

        PayTransactionVO vo = payService.mockPay(USER_ID, OUT_TRADE_NO);

        assertThat(vo.getStatus()).isEqualTo(PayStatusEnum.SUCCESS.getCode());
        ArgumentCaptor<com.hongcheng.pay.dto.PayCallbackRequest> captor =
                ArgumentCaptor.forClass(com.hongcheng.pay.dto.PayCallbackRequest.class);
        verify(payCallbackService).handle(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("99.90");
        assertThat(captor.getValue().getSuccess()).isTrue();
        assertThat(captor.getValue().getThirdTradeNo()).startsWith("MOCK");
    }

    @Test
    @DisplayName("查询支付单 - 非本人抛 1405")
    void detail_whenNotOwner_throwsAccessDenied() {
        when(payTransactionMapper.selectByOutTradeNo(OUT_TRADE_NO))
                .thenReturn(transaction(2L, PayStatusEnum.PAYING.getCode()));

        assertThatThrownBy(() -> payService.detail(USER_ID, OUT_TRADE_NO))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1405));
    }

    @Test
    @DisplayName("查询支付单 - 不存在抛 1401")
    void detail_whenMissing_throwsNotFound() {
        when(payTransactionMapper.selectByOutTradeNo(OUT_TRADE_NO)).thenReturn(null);

        assertThatThrownBy(() -> payService.detail(USER_ID, OUT_TRADE_NO))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1401));
    }

    private OrderPayInfo order(Long userId, int status, String amount) {
        OrderPayInfo info = new OrderPayInfo();
        info.setOrderNo(ORDER_NO);
        info.setUserId(userId);
        info.setPayAmount(new BigDecimal(amount));
        info.setStatus(status);
        return info;
    }

    private PayTransaction transaction(Long userId, int status) {
        PayTransaction transaction = new PayTransaction();
        transaction.setId(900000000000000001L);
        transaction.setOutTradeNo(OUT_TRADE_NO);
        transaction.setOrderNo(ORDER_NO);
        transaction.setUserId(userId);
        transaction.setAmount(new BigDecimal("99.90"));
        transaction.setChannel("MOCK");
        transaction.setStatus(status);
        return transaction;
    }
}

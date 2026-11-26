package com.hongcheng.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongcheng.common.enums.OrderStatusEnum;
import com.hongcheng.common.enums.OrderTypeEnum;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.order.dto.OrderAdminVO;
import com.hongcheng.order.dto.OrderCreateStateVO;
import com.hongcheng.order.dto.UserBriefVO;
import com.hongcheng.order.entity.Order;
import com.hongcheng.order.entity.OrderItem;
import com.hongcheng.order.entity.OrderStatusLog;
import com.hongcheng.order.feign.UserBriefClient;
import com.hongcheng.order.mapper.OrderItemMapper;
import com.hongcheng.order.mapper.OrderMapper;
import com.hongcheng.order.mapper.OrderStatusLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单领域服务测试：状态机流转、幂等、归属校验、取消回补库存。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final String ORDER_NO = "HC900000000000000001";
    private static final Long ORDER_ID = 900000000000000001L;
    private static final Long USER_ID = 1L;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private OrderStatusLogMapper orderStatusLogMapper;

    @Mock
    private OrderStateStore orderStateStore;

    @Mock
    private OrderStockService orderStockService;

    @Mock
    private OrderProgressPublisher orderProgressPublisher;

    @Mock
    private UserBriefClient userBriefClient;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("取消待支付订单 - 条件更新命中后回补库存并写 CANCEL_USER 日志")
    void cancel_whenPendingPayment_restoresStockAndWritesLog() {
        Order order = order(OrderStatusEnum.PENDING_PAYMENT.getCode());
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderMapper.markCancelled(eq(ORDER_ID), eq(0), eq("用户取消"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));

        orderService.cancel(ORDER_NO, USER_ID);

        verify(orderStockService).restoreWithRetry(anyList(), eq(ORDER_NO));
        ArgumentCaptor<OrderStatusLog> captor = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(orderStatusLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getEvent()).isEqualTo("CANCEL_USER");
        assertThat(captor.getValue().getFromStatus()).isEqualTo(0);
        assertThat(captor.getValue().getToStatus()).isEqualTo(40);
    }

    @Test
    @DisplayName("取消已支付订单 - 抛 1302 且不回补库存")
    void cancel_whenPaid_throwsStatusInvalid() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order(OrderStatusEnum.PAID.getCode()));

        assertThatThrownBy(() -> orderService.cancel(ORDER_NO, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1302));
        verify(orderStockService, never()).restoreWithRetry(anyList(), any());
    }

    @Test
    @DisplayName("操作他人订单 - 抛 1303 且不落任何状态")
    void cancel_whenNotOwner_throwsAccessDenied() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order(OrderStatusEnum.PENDING_PAYMENT.getCode()));

        assertThatThrownBy(() -> orderService.cancel(ORDER_NO, 2L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1303));
        verify(orderMapper, never()).markCancelled(any(), any(), any(), any());
    }

    @Test
    @DisplayName("支付回调 - 待支付订单置为已支付并写 PAY 日志")
    void markPaid_whenPendingPayment_transitsToPaid() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order(OrderStatusEnum.PENDING_PAYMENT.getCode()));
        when(orderMapper.markPaid(eq(ORDER_ID), any(LocalDateTime.class))).thenReturn(1);

        orderService.markPaid(ORDER_NO);

        ArgumentCaptor<OrderStatusLog> captor = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(orderStatusLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getEvent()).isEqualTo("PAY");
        assertThat(captor.getValue().getToStatus()).isEqualTo(10);
    }

    @Test
    @DisplayName("支付回调重复到达 - 已支付订单幂等返回且不重复写日志")
    void markPaid_whenAlreadyPaid_isIdempotent() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order(OrderStatusEnum.PAID.getCode()));

        orderService.markPaid(ORDER_NO);

        verify(orderMapper, never()).markPaid(any(), any());
        verify(orderStatusLogMapper, never()).insert(any(OrderStatusLog.class));
    }

    @Test
    @DisplayName("确认完成 - 服务中订单置为已完成并写 FINISH 日志")
    void complete_whenInService_transitsToCompleted() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order(OrderStatusEnum.IN_SERVICE.getCode()));
        when(orderMapper.markCompleted(eq(ORDER_ID), any(LocalDateTime.class))).thenReturn(1);

        orderService.complete(ORDER_NO, USER_ID);

        ArgumentCaptor<OrderStatusLog> captor = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(orderStatusLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getEvent()).isEqualTo("FINISH");
        assertThat(captor.getValue().getToStatus()).isEqualTo(30);
    }

    @Test
    @DisplayName("演示发货 - 本人已支付订单进入服务中并写 DELIVER 日志")
    void demoDeliver_whenPaidAndOwner_transitsToInService() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order(OrderStatusEnum.PAID.getCode()));
        when(orderMapper.markDelivered(ORDER_ID)).thenReturn(1);

        orderService.demoDeliver(ORDER_NO, USER_ID);

        verify(orderMapper).markDelivered(ORDER_ID);
        ArgumentCaptor<OrderStatusLog> captor = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(orderStatusLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getEvent()).isEqualTo("DELIVER");
        assertThat(captor.getValue().getToStatus()).isEqualTo(20);
    }

    @Test
    @DisplayName("演示发货 - 他人订单抛 1303 且不发货")
    void demoDeliver_whenNotOwner_throwsAccessDenied() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order(OrderStatusEnum.PAID.getCode()));

        assertThatThrownBy(() -> orderService.demoDeliver(ORDER_NO, 2L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1303));
        verify(orderMapper, never()).markDelivered(any());
        verify(orderStatusLogMapper, never()).insert(any(OrderStatusLog.class));
    }

    @Test
    @DisplayName("超时取消 - 待支付订单取消并回补库存，写 CANCEL_TIMEOUT 日志")
    void timeoutCancel_whenPendingPayment_cancelsAndRestores() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order(OrderStatusEnum.PENDING_PAYMENT.getCode()));
        when(orderMapper.markCancelled(eq(ORDER_ID), eq(0), eq("超时未支付自动取消"),
                any(LocalDateTime.class))).thenReturn(1);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));

        orderService.timeoutCancel(ORDER_NO);

        verify(orderStockService).restoreWithRetry(anyList(), eq(ORDER_NO));
        ArgumentCaptor<OrderStatusLog> captor = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(orderStatusLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getEvent()).isEqualTo("CANCEL_TIMEOUT");
        assertThat(captor.getValue().getOperatorType()).isEqualTo(2);
    }

    @Test
    @DisplayName("超时取消 - 已支付订单直接忽略，不取消不回补")
    void timeoutCancel_whenPaid_ignored() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order(OrderStatusEnum.PAID.getCode()));

        orderService.timeoutCancel(ORDER_NO);

        verify(orderMapper, never()).markCancelled(any(), any(), any(), any());
        verify(orderStockService, never()).restoreWithRetry(anyList(), any());
    }

    @Test
    @DisplayName("查询排队中的订单 - 返回 1304 创建中提示")
    void detail_whenQueued_throwsCreating() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);
        when(orderStateStore.get(ORDER_NO)).thenReturn(new OrderCreateStateVO(
                ORDER_NO, OrderStateStore.STATE_QUEUED, "排队中", LocalDateTime.now()));

        assertThatThrownBy(() -> orderService.detail(ORDER_NO, USER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1304));
    }

    @Test
    @DisplayName("订单详情 - 支付截止时间与延迟关单配置一致并返回服务端时间")
    void detail_whenExists_returnsPayDeadlineAndServerTime() {
        Order order = order(OrderStatusEnum.PENDING_PAYMENT.getCode());
        LocalDateTime createTime = LocalDateTime.of(2026, 11, 16, 12, 0, 0);
        order.setCreateTime(createTime);
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));

        var vo = orderService.detail(ORDER_NO, USER_ID);

        assertThat(vo.getPayDeadline()).isEqualTo(createTime.plusSeconds(1800));
        assertThat(vo.getServerTime()).isNotNull();
    }

    @Test
    @DisplayName("下单状态轮询 - 非本人查询 DB 已建单抛 1303，ADMIN 可查")
    void state_whenDbOrderOwnedByOther_deniesUserAllowsAdmin() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order(OrderStatusEnum.PENDING_PAYMENT.getCode()));

        assertThatThrownBy(() -> orderService.state(ORDER_NO, 2L, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1303));

        OrderCreateStateVO adminVo = orderService.state(ORDER_NO, 99L, true);
        assertThat(adminVo.getState()).isEqualTo(OrderStateStore.STATE_CREATED);
    }

    @Test
    @DisplayName("下单状态轮询 - Redis 暂存态同样校验归属且对外隐藏 userId")
    void state_whenQueuedInRedis_checksOwnerAndMasksUserId() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);
        when(orderStateStore.get(ORDER_NO)).thenReturn(
                new OrderCreateStateVO(ORDER_NO, OrderStateStore.STATE_QUEUED, "排队中", LocalDateTime.now(), USER_ID));

        assertThatThrownBy(() -> orderService.state(ORDER_NO, 2L, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1303));

        OrderCreateStateVO ownerVo = orderService.state(ORDER_NO, USER_ID, false);
        assertThat(ownerVo.getState()).isEqualTo(OrderStateStore.STATE_QUEUED);
        assertThat(ownerVo.getUserId()).isNull();
    }

    @Test
    @DisplayName("内部支付查询 - 返回订单归属、应付金额与状态")
    void payInfo_whenExists_returnsPayInfo() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order(OrderStatusEnum.PENDING_PAYMENT.getCode()));

        var info = orderService.payInfo(ORDER_NO);

        assertThat(info.getOrderNo()).isEqualTo(ORDER_NO);
        assertThat(info.getUserId()).isEqualTo(USER_ID);
        assertThat(info.getPayAmount()).isEqualByComparingTo("20.00");
        assertThat(info.getStatus()).isEqualTo(0);
    }

    @Test
    @DisplayName("内部支付查询 - 订单不存在抛 1301")
    void payInfo_whenMissing_throwsNotFound() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);

        assertThatThrownBy(() -> orderService.payInfo(ORDER_NO))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1301));
    }

    @Test
    @DisplayName("内部评价上下文 - 返回订单归属/状态/明细快照")
    void reviewContext_whenExists_returnsContext() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order(OrderStatusEnum.COMPLETED.getCode()));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));

        var context = orderService.reviewContext(ORDER_NO);

        assertThat(context.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(context.getUserId()).isEqualTo(USER_ID);
        assertThat(context.getStatus()).isEqualTo(30);
        assertThat(context.getItems()).hasSize(1);
        assertThat(context.getItems().get(0).getItemId()).isEqualTo(90001L);
        assertThat(context.getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("内部评价上下文 - 订单不存在抛 1301")
    void reviewContext_whenMissing_throwsNotFound() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);

        assertThatThrownBy(() -> orderService.reviewContext(ORDER_NO))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1301));
    }

    @Test
    @DisplayName("管理员门店接单 - 服务订单已支付时进入服务中并推送进度")
    void adminAccept_whenServiceOrderPaid_transitsAndPublishes() {
        Order order = order(OrderStatusEnum.PAID.getCode());
        order.setOrderType(OrderTypeEnum.SERVICE.getCode());
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderMapper.markDelivered(ORDER_ID)).thenReturn(1);

        orderService.adminAccept(ORDER_NO);

        verify(orderMapper).markDelivered(ORDER_ID);
        ArgumentCaptor<OrderStatusLog> captor = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(orderStatusLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getEvent()).isEqualTo("DELIVER");
        assertThat(captor.getValue().getToStatus()).isEqualTo(20);
        verify(orderProgressPublisher).publish(any(), eq("ORDER_DELIVERED"), any(), any());
    }

    @Test
    @DisplayName("管理员门店接单 - 商品订单抛 1307 且不流转")
    void adminAccept_whenProductOrder_throwsTypeUnsupported() {
        Order order = order(OrderStatusEnum.PAID.getCode());
        order.setOrderType(OrderTypeEnum.PRODUCT.getCode());
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order);

        assertThatThrownBy(() -> orderService.adminAccept(ORDER_NO))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1307));
        verify(orderMapper, never()).markDelivered(any());
    }

    @Test
    @DisplayName("管理员商家发货 - 商品订单已支付时进入已发货并推送进度")
    void adminDeliver_whenProductOrderPaid_transitsAndPublishes() {
        Order order = order(OrderStatusEnum.PAID.getCode());
        order.setOrderType(OrderTypeEnum.PRODUCT.getCode());
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order);
        when(orderMapper.markDelivered(ORDER_ID)).thenReturn(1);

        orderService.adminDeliver(ORDER_NO);

        verify(orderMapper).markDelivered(ORDER_ID);
        verify(orderProgressPublisher).publish(any(), eq("ORDER_DELIVERED"), any(), any());
    }

    @Test
    @DisplayName("管理员商家发货 - 服务订单抛 1307 且不流转")
    void adminDeliver_whenServiceOrder_throwsTypeUnsupported() {
        Order order = order(OrderStatusEnum.PAID.getCode());
        order.setOrderType(OrderTypeEnum.SERVICE.getCode());
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order);

        assertThatThrownBy(() -> orderService.adminDeliver(ORDER_NO))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1307));
        verify(orderMapper, never()).markDelivered(any());
    }

    @Test
    @DisplayName("管理员操作不存在订单 - 抛 1301")
    void adminAccept_whenMissing_throwsNotFound() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);

        assertThatThrownBy(() -> orderService.adminAccept(ORDER_NO))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(1301));
    }

    @Test
    @DisplayName("管理员订单分页 - 补全真实用户名/昵称且预约手机号/联系人部分脱敏")
    void adminPage_enrichesUsernameAndMasksSensitiveFields() {
        Order order = order(OrderStatusEnum.PAID.getCode());
        order.setAppointmentPhone("13812345678");
        order.setAppointmentContact("张三");
        Page<Order> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(order));
        when(orderMapper.selectPage(any(), any())).thenReturn(page);
        when(orderItemMapper.selectList(any())).thenReturn(List.of());
        UserBriefVO brief = new UserBriefVO();
        brief.setId(USER_ID);
        brief.setUsername("zhangsan");
        brief.setNickname("张三");
        when(userBriefClient.briefs("1")).thenReturn(Result.ok(List.of(brief)));

        Page<OrderAdminVO> result = orderService.adminPage(10, null, 1, 10);

        OrderAdminVO vo = result.getRecords().get(0);
        assertThat(vo.getUserId()).isEqualTo(USER_ID);
        assertThat(vo.getUsername()).isEqualTo("zhangsan");
        assertThat(vo.getNickname()).isEqualTo("张三");
        assertThat(vo.getAppointmentPhone()).isEqualTo("138****5678");
        assertThat(vo.getAppointmentContact()).isEqualTo("张**");
    }

    @Test
    @DisplayName("管理员订单分页 - 用户简要降级 503 时抛业务异常，不退化为匿名展示")
    void adminPage_whenBriefFallback_throwsServiceUnavailable() {
        Order order = order(OrderStatusEnum.PAID.getCode());
        Page<Order> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(order));
        when(orderMapper.selectPage(any(), any())).thenReturn(page);
        when(orderItemMapper.selectList(any())).thenReturn(List.of());
        when(userBriefClient.briefs(any()))
                .thenReturn(Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(), "用户服务繁忙"));

        assertThatThrownBy(() -> orderService.adminPage(null, null, 1, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户服务暂不可用");
    }

    private Order order(int status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo(ORDER_NO);
        order.setUserId(USER_ID);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("20.00"));
        order.setPayAmount(new BigDecimal("20.00"));
        return order;
    }

    private OrderItem item() {
        OrderItem item = new OrderItem();
        item.setItemId(90001L);
        item.setOrderNo(ORDER_NO);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("10.00"));
        item.setTotalAmount(new BigDecimal("20.00"));
        return item;
    }
}

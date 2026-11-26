package com.hongcheng.order.service;

import com.hongcheng.common.result.Result;
import com.hongcheng.order.dto.OrderCreateMessage;
import com.hongcheng.order.dto.OrderItemRequest;
import com.hongcheng.order.dto.ProductInfo;
import com.hongcheng.order.dto.ServiceItemInfo;
import com.hongcheng.order.dto.StockDeductRequest;
import com.hongcheng.order.entity.Order;
import com.hongcheng.order.feign.ProductClient;
import com.hongcheng.order.feign.ShopClient;
import com.hongcheng.order.mapper.OrderMapper;
import com.hongcheng.order.mq.OrderMqConstants;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 下单削峰消费者测试：幂等、业务失败、成功建单、落库失败回补。
 */
@ExtendWith(MockitoExtension.class)
class OrderCreateProcessorTest {

    private static final String ORDER_NO = "HC900000000000000002";
    private static final Long ORDER_ID = 900000000000000002L;
    private static final Long PRODUCT_ID = 90001L;
    private static final Long SERVICE_ITEM_ID = 91001L;
    private static final Long SHOP_ID = 92001L;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderService orderService;

    @Mock
    private ProductClient productClient;

    @Mock
    private ShopClient shopClient;

    @Mock
    private OrderStockService orderStockService;

    @Mock
    private OrderStateStore orderStateStore;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    private OrderCreateProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new OrderCreateProcessor(orderMapper, orderService, productClient, shopClient,
                orderStockService, orderStateStore, rocketMQTemplate, 4);
    }

    @Test
    @DisplayName("消息重复投递 - 订单已存在时跳过，不再查商品/扣库存")
    void process_whenOrderExists_skips() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(new Order());

        processor.process(message());

        verify(orderStateStore).markCreated(ORDER_NO, 1L);
        verifyNoInteractions(productClient, shopClient, orderStockService, orderService, rocketMQTemplate);
    }

    @Test
    @DisplayName("商品已下架 - 标记 FAILED，不扣库存不建单")
    void process_whenProductUnavailable_marksFailed() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);
        when(productClient.detail(PRODUCT_ID)).thenReturn(Result.fail(1102, "商品不存在或已下架"));

        processor.process(message());

        verify(orderStateStore).markFailed(eq(ORDER_NO), eq(1L), eq("商品不存在或已下架"));
        verify(orderStockService, never()).deduct(any());
        verify(orderService, never()).persist(any(), anyList());
        verifyNoInteractions(rocketMQTemplate);
    }

    @Test
    @DisplayName("库存不足 - 标记 FAILED，不建单、不发超时消息")
    void process_whenStockNotEnough_marksFailed() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);
        when(productClient.detail(PRODUCT_ID)).thenReturn(Result.ok(product()));
        when(orderStockService.toStockRequest(anyList())).thenReturn(new StockDeductRequest());
        when(orderStockService.deduct(any())).thenReturn(Result.fail(1101, "库存不足: 商品90001"));

        processor.process(message());

        verify(orderStateStore).markFailed(eq(ORDER_NO), eq(1L), eq("库存不足: 商品90001"));
        verify(orderService, never()).persist(any(), anyList());
        verifyNoInteractions(rocketMQTemplate);
    }

    @Test
    @DisplayName("正常下单 - 价格快照汇总、落库、标记 CREATED 并发 30s 延迟消息")
    void process_whenSuccess_persistsOrderAndSendsDelayMessage() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);
        when(productClient.detail(PRODUCT_ID)).thenReturn(Result.ok(product()));
        when(orderStockService.toStockRequest(anyList())).thenReturn(new StockDeductRequest());
        when(orderStockService.deduct(any())).thenReturn(Result.ok());

        processor.process(message());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderService).persist(orderCaptor.capture(), anyList());
        Order order = orderCaptor.getValue();
        assertThat(order.getOrderNo()).isEqualTo(ORDER_NO);
        assertThat(order.getStatus()).isEqualTo(0);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("20.00");
        assertThat(order.getPayAmount()).isEqualByComparingTo("20.00");
        verify(orderStateStore).markCreated(ORDER_NO, 1L);
        verify(rocketMQTemplate).syncSend(eq(OrderMqConstants.TIMEOUT_TOPIC),
                any(Message.class), eq(3000L), eq(4));
    }

    @Test
    @DisplayName("落库异常 - 回补库存后抛出，交由 MQ 重试")
    void process_whenPersistFails_restoresStockAndRethrows() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);
        when(productClient.detail(PRODUCT_ID)).thenReturn(Result.ok(product()));
        when(orderStockService.toStockRequest(anyList())).thenReturn(new StockDeductRequest());
        when(orderStockService.deduct(any())).thenReturn(Result.ok());
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(orderService).persist(any(), anyList());

        assertThatThrownBy(() -> processor.process(message()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");

        verify(orderStockService).restoreWithRetry(anyList(), eq(ORDER_NO));
        verify(orderStateStore, never()).markCreated(anyString(), any());
        verifyNoInteractions(rocketMQTemplate);
    }

    @Test
    @DisplayName("服务预约订单 - 记录预约信息、不扣商品库存、正常建单")
    void process_whenServiceOrder_skipsStockAndSavesAppointment() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);
        when(shopClient.detail(SERVICE_ITEM_ID)).thenReturn(Result.ok(serviceItem()));

        processor.process(serviceMessage());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderService).persist(orderCaptor.capture(), anyList());
        Order order = orderCaptor.getValue();
        assertThat(order.getOrderType()).isEqualTo(2);
        assertThat(order.getShopId()).isEqualTo(SHOP_ID);
        assertThat(order.getAppointmentTime()).isEqualTo(LocalDateTime.of(2027, 1, 1, 10, 30));
        assertThat(order.getAppointmentContact()).isEqualTo("张三");
        assertThat(order.getAppointmentPhone()).isEqualTo("13800138000");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("99.00");
        verify(orderStockService, never()).deduct(any());
        verify(orderStockService, never()).toStockRequest(anyList());
        verify(orderStateStore).markCreated(ORDER_NO, 1L);
        verify(rocketMQTemplate).syncSend(eq(OrderMqConstants.TIMEOUT_TOPIC),
                any(Message.class), eq(3000L), eq(4));
    }

    @Test
    @DisplayName("服务项目与门店不匹配 - 标记 FAILED，不建单不扣库存")
    void process_whenServiceBelongsToAnotherShop_marksFailed() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);
        ServiceItemInfo item = serviceItem();
        item.setShopId(SHOP_ID + 1);
        when(shopClient.detail(SERVICE_ITEM_ID)).thenReturn(Result.ok(item));

        processor.process(serviceMessage());

        verify(orderStateStore).markFailed(eq(ORDER_NO), eq(1L), eq("服务项目不属于所选门店"));
        verify(orderService, never()).persist(any(), anyList());
        verify(orderStockService, never()).deduct(any());
        verifyNoInteractions(rocketMQTemplate);
    }

    private OrderCreateMessage message() {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductId(PRODUCT_ID);
        item.setQuantity(2);
        return new OrderCreateMessage(ORDER_ID, ORDER_NO, 1L, null, 1, List.of(item),
                null, null, null, "单测");
    }

    private OrderCreateMessage serviceMessage() {
        OrderItemRequest item = new OrderItemRequest();
        item.setServiceItemId(SERVICE_ITEM_ID);
        item.setQuantity(1);
        return new OrderCreateMessage(ORDER_ID, ORDER_NO, 1L, SHOP_ID, 2, List.of(item),
                LocalDateTime.of(2027, 1, 1, 10, 30), "张三", "13800138000", "预约保养");
    }

    private ProductInfo product() {
        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductName("验收商品");
        product.setCoverImg("/images/test.jpg");
        product.setPrice(new BigDecimal("10.00"));
        product.setStatus(1);
        return product;
    }

    private ServiceItemInfo serviceItem() {
        ServiceItemInfo item = new ServiceItemInfo();
        item.setId(SERVICE_ITEM_ID);
        item.setShopId(SHOP_ID);
        item.setShopName("验收门店");
        item.setItemName("小保养");
        item.setCoverImg("/images/service.jpg");
        item.setPrice(new BigDecimal("99.00"));
        item.setStatus(1);
        return item;
    }
}

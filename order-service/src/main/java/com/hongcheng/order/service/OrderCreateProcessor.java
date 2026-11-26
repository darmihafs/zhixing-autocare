package com.hongcheng.order.service;

import com.hongcheng.common.enums.OrderStatusEnum;
import com.hongcheng.common.enums.OrderTypeEnum;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.order.dto.OrderCreateMessage;
import com.hongcheng.order.dto.OrderItemRequest;
import com.hongcheng.order.dto.OrderTimeoutMessage;
import com.hongcheng.order.dto.ProductInfo;
import com.hongcheng.order.dto.ServiceItemInfo;
import com.hongcheng.order.dto.StockDeductRequest;
import com.hongcheng.order.entity.Order;
import com.hongcheng.order.entity.OrderItem;
import com.hongcheng.order.feign.ProductClient;
import com.hongcheng.order.feign.ShopClient;
import com.hongcheng.order.mapper.OrderMapper;
import com.hongcheng.order.mq.OrderMqConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 下单消息处理器（消费者线程执行）：价格快照 → Feign 扣库存 → 订单落库 → 延迟消息。
 * <p>
 * 幂等与失败语义：
 * <ul>
 *   <li>订单已存在（消息重复投递）→ 直接标记 CREATED 并返回，不会二次扣库存；</li>
 *   <li>商品下架/不存在、库存不足 → 标记 FAILED 并正常返回（业务失败不重试）；</li>
 *   <li>Feign 网络异常、落库异常 → 回补已扣库存后抛出，交由 RocketMQ 重试；</li>
 *   <li>落库成功但延迟消息发送失败 → 只记 error，订单仍有效（需人工/补偿取消）。</li>
 * </ul>
 */
@Slf4j
@Component
public class OrderCreateProcessor {

    private static final long SEND_TIMEOUT_MILLIS = 3000L;

    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final ProductClient productClient;
    private final ShopClient shopClient;
    private final OrderStockService orderStockService;
    private final OrderStateStore orderStateStore;
    private final RocketMQTemplate rocketMQTemplate;
    private final int payTimeoutDelayLevel;

    public OrderCreateProcessor(OrderMapper orderMapper,
                                OrderService orderService,
                                ProductClient productClient,
                                ShopClient shopClient,
                                OrderStockService orderStockService,
                                OrderStateStore orderStateStore,
                                RocketMQTemplate rocketMQTemplate,
                                @Value("${hc.order.pay-timeout-delay-level:16}") int payTimeoutDelayLevel) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.productClient = productClient;
        this.shopClient = shopClient;
        this.orderStockService = orderStockService;
        this.orderStateStore = orderStateStore;
        this.rocketMQTemplate = rocketMQTemplate;
        this.payTimeoutDelayLevel = payTimeoutDelayLevel;
    }

    public void process(OrderCreateMessage message) {
        String orderNo = message.getOrderNo();
        Long userId = message.getUserId();
        if (orderMapper.selectByOrderNo(orderNo) != null) {
            orderStateStore.markCreated(orderNo, userId);
            log.info("订单已存在，跳过重复消费 orderNo={}", orderNo);
            return;
        }

        boolean serviceOrder = Integer.valueOf(OrderTypeEnum.SERVICE.getCode())
                .equals(message.getOrderType());
        List<OrderItem> items = new ArrayList<>(message.getItems().size());
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItemRequest request : message.getItems()) {
            OrderItem item = serviceOrder
                    ? buildServiceItem(orderNo, userId, request, message.getShopId())
                    : buildProductItem(orderNo, userId, request);
            if (item == null) {
                return;
            }
            items.add(item);
            totalAmount = totalAmount.add(item.getTotalAmount());
        }

        if (!serviceOrder) {
            StockDeductRequest stockRequest = orderStockService.toStockRequest(items);
            Result<Void> deductResult = orderStockService.deduct(stockRequest);
            if (deductResult == null || !deductResult.isSuccess()) {
                String reason = deductResult == null ? "库存服务无响应" : deductResult.getMessage();
                if (deductResult == null || deductResult.getCode() == ResultCode.SERVICE_UNAVAILABLE.getCode()) {
                    // 下游不可用：抛出让 MQ 重试，避免库存未扣却把订单标记为失败
                    throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "库存服务暂不可用");
                }
                orderStateStore.markFailed(orderNo, userId, reason);
                log.warn("扣库存失败，订单创建终止 orderNo={}, code={}, message={}",
                        orderNo, deductResult == null ? null : deductResult.getCode(), reason);
                return;
            }
        } else {
            log.info("服务预约订单无需扣减商品库存 orderNo={}, serviceItemId={}",
                    orderNo, items.get(0).getItemId());
        }

        Order order = buildOrder(message, totalAmount);
        try {
            orderService.persist(order, items);
        } catch (Exception e) {
            log.error("订单落库失败，回补库存后交由 MQ 重试 orderNo={}", orderNo, e);
            orderStockService.restoreWithRetry(items, orderNo);
            throw e;
        }

        orderStateStore.markCreated(orderNo, userId);
        sendTimeoutMessage(orderNo);
        log.info("订单创建成功 orderNo={}, userId={}, totalAmount={}",
                orderNo, message.getUserId(), totalAmount);
    }

    /** 查询商品快照；失败会标记 FAILED 并返回 null（调用方终止处理） */
    private ProductInfo fetchProduct(String orderNo, Long userId, Long productId) {
        Result<ProductInfo> result;
        try {
            result = productClient.detail(productId);
        } catch (Exception e) {
            log.error("查询商品失败，交由 MQ 重试 orderNo={}, productId={}", orderNo, productId, e);
            throw e;
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            String reason = result == null ? "商品信息查询失败" : result.getMessage();
            if (result == null || result.getCode() == ResultCode.SERVICE_UNAVAILABLE.getCode()) {
                // 下游不可用：抛出让 MQ 重试，不把订单误标记为终止
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "商品服务暂不可用");
            }
            orderStateStore.markFailed(orderNo, userId, reason);
            log.warn("商品不可下单，订单创建终止 orderNo={}, productId={}, message={}",
                    orderNo, productId, reason);
            return null;
        }
        return result.getData();
    }

    /** 查询门店服务项目快照；失败语义与商品查询一致 */
    private ServiceItemInfo fetchServiceItem(String orderNo, Long userId, Long serviceItemId) {
        Result<ServiceItemInfo> result;
        try {
            result = shopClient.detail(serviceItemId);
        } catch (Exception e) {
            log.error("查询服务项目失败，交由 MQ 重试 orderNo={}, serviceItemId={}", orderNo, serviceItemId, e);
            throw e;
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            String reason = result == null ? "服务项目信息查询失败" : result.getMessage();
            if (result == null || result.getCode() == ResultCode.SERVICE_UNAVAILABLE.getCode()) {
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "门店服务暂不可用");
            }
            orderStateStore.markFailed(orderNo, userId, reason);
            log.warn("服务项目不可预约，订单创建终止 orderNo={}, serviceItemId={}, message={}",
                    orderNo, serviceItemId, reason);
            return null;
        }
        return result.getData();
    }

    private OrderItem buildProductItem(String orderNo, Long userId, OrderItemRequest request) {
        ProductInfo product = fetchProduct(orderNo, userId, request.getProductId());
        if (product == null) {
            return null;
        }
        OrderItem item = new OrderItem();
        item.setItemType(OrderTypeEnum.PRODUCT.getCode());
        item.setItemId(product.getId());
        item.setItemName(product.getProductName());
        item.setItemImg(product.getCoverImg());
        item.setPrice(product.getPrice());
        item.setQuantity(request.getQuantity());
        item.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
        return item;
    }

    private OrderItem buildServiceItem(String orderNo, Long userId, OrderItemRequest request, Long shopId) {
        ServiceItemInfo service = fetchServiceItem(orderNo, userId, request.getServiceItemId());
        if (service == null) {
            return null;
        }
        if (shopId != null && !shopId.equals(service.getShopId())) {
            orderStateStore.markFailed(orderNo, userId, "服务项目不属于所选门店");
            log.warn("服务项目与门店不匹配，订单创建终止 orderNo={}, shopId={}, serviceItemId={}, actualShopId={}",
                    orderNo, shopId, service.getId(), service.getShopId());
            return null;
        }
        OrderItem item = new OrderItem();
        item.setItemType(OrderTypeEnum.SERVICE.getCode());
        item.setItemId(service.getId());
        item.setItemName(service.getItemName());
        item.setItemImg(service.getCoverImg());
        item.setPrice(service.getPrice());
        item.setQuantity(1);
        item.setTotalAmount(service.getPrice());
        return item;
    }

    private Order buildOrder(OrderCreateMessage message, BigDecimal totalAmount) {
        Order order = new Order();
        order.setId(message.getOrderId());
        order.setOrderNo(message.getOrderNo());
        order.setUserId(message.getUserId());
        order.setShopId(message.getShopId());
        order.setOrderType(message.getOrderType());
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);
        order.setStatus(OrderStatusEnum.PENDING_PAYMENT.getCode());
        order.setAppointmentTime(message.getAppointmentTime());
        order.setAppointmentContact(message.getAppointmentContact());
        order.setAppointmentPhone(message.getAppointmentPhone());
        order.setRemark(message.getRemark());
        return order;
    }

    /** 延迟消息：payTimeoutDelayLevel 个等级后触发超时取消（失败只记录，不阻塞建单） */
    private void sendTimeoutMessage(String orderNo) {
        Message<OrderTimeoutMessage> timeoutMessage = MessageBuilder
                .withPayload(new OrderTimeoutMessage(orderNo))
                .build();
        try {
            rocketMQTemplate.syncSend(OrderMqConstants.TIMEOUT_TOPIC, timeoutMessage,
                    SEND_TIMEOUT_MILLIS, payTimeoutDelayLevel);
            log.info("超时取消延迟消息已发送 orderNo={}, delayLevel={}", orderNo, payTimeoutDelayLevel);
        } catch (Exception e) {
            log.error("超时取消延迟消息发送失败，订单需人工补偿取消 orderNo={}", orderNo, e);
        }
    }
}

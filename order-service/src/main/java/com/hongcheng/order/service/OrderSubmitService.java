package com.hongcheng.order.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.hongcheng.common.enums.OrderTypeEnum;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.order.dto.CreateOrderRequest;
import com.hongcheng.order.dto.OrderCreateMessage;
import com.hongcheng.order.dto.OrderCreateStateVO;
import com.hongcheng.order.dto.OrderItemRequest;
import com.hongcheng.order.mq.OrderMqConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 下单入口：只做参数校验与入队，不在请求线程里访问商品服务/数据库。
 * <p>
 * 削峰原理：突发流量全部变成一次 Redis SET + 一次同步 send（毫秒级），
 * 真正的价格查询、Feign 扣库存、订单落库由固定并发度的消费者按能力消费，
 * 削掉数据库与下游服务承受的瞬时峰值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSubmitService {

    private static final long SEND_TIMEOUT_MILLIS = 3000L;

    private final OrderStateStore orderStateStore;
    private final RocketMQTemplate rocketMQTemplate;

    @SentinelResource(value = "orderCreate", blockHandler = "createBlocked")
    public OrderCreateStateVO submit(Long userId, CreateOrderRequest request) {
        validate(request);
        long orderId = IdWorker.getId();
        String orderNo = "HC" + orderId;
        OrderCreateMessage message = new OrderCreateMessage(orderId, orderNo, userId, request.getShopId(),
                request.getOrderType(), request.getItems(), request.getAppointmentTime(),
                request.getAppointmentContact(), request.getAppointmentPhone(), request.getRemark());

        orderStateStore.markQueued(orderNo, userId);
        try {
            rocketMQTemplate.syncSend(OrderMqConstants.CREATE_TOPIC, message, SEND_TIMEOUT_MILLIS);
        } catch (Exception e) {
            log.error("下单消息发送失败 orderNo={}", orderNo, e);
            orderStateStore.markFailed(orderNo, userId, "下单请求提交失败，请重试");
            throw new BusinessException(ResultCode.ORDER_SUBMIT_FAILED);
        }
        log.info("下单请求已入队 orderNo={}, userId={}, itemCount={}",
                orderNo, userId, request.getItems().size());
        return new OrderCreateStateVO(orderNo, OrderStateStore.STATE_QUEUED,
                "已进入下单队列，处理完成后可查询订单", LocalDateTime.now());
    }

    /**
     * 下单入口被 Sentinel 流控/熔断拦截时的兜底：返回明确的 1308，
     * 不抛 500，也不进入 MQ 队列，避免突发流量把堆积传导到下游。
     */
    public OrderCreateStateVO createBlocked(Long userId, CreateOrderRequest request, BlockException e) {
        log.warn("下单请求被 Sentinel 拦截 userId={}, blockType={}", userId, e.getClass().getSimpleName());
        throw new BusinessException(ResultCode.ORDER_RATE_LIMITED);
    }

    private void validate(CreateOrderRequest request) {
        OrderTypeEnum type = OrderTypeEnum.of(request.getOrderType());
        if (type == null) {
            throw new BusinessException(ResultCode.ORDER_TYPE_UNSUPPORTED);
        }
        List<OrderItemRequest> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "订单明细不能为空");
        }
        if (type == OrderTypeEnum.PRODUCT) {
            validateProductItems(items);
            return;
        }
        validateServiceOrder(request, items);
    }

    private void validateProductItems(List<OrderItemRequest> items) {
        if (items.stream().anyMatch(item -> item.getProductId() == null)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品订单缺少商品ID");
        }
        long distinctProductIds = items.stream()
                .map(OrderItemRequest::getProductId)
                .distinct()
                .count();
        if (distinctProductIds != items.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "同一商品请合并数量后下单");
        }
    }

    private void validateServiceOrder(CreateOrderRequest request, List<OrderItemRequest> items) {
        if (request.getShopId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "服务订单缺少门店ID");
        }
        if (items.size() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "一次预约只能提交一个服务项目");
        }
        OrderItemRequest item = items.get(0);
        if (item.getServiceItemId() == null || item.getProductId() != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "服务订单缺少服务项目ID");
        }
        if (!Objects.equals(item.getQuantity(), 1)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "服务项目预约数量只能为1");
        }
        if (request.getAppointmentTime() == null
                || !request.getAppointmentTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择晚于当前时间的预约到店时间");
        }
        if (!StringUtils.hasText(request.getAppointmentContact())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请填写预约联系人");
        }
        if (!StringUtils.hasText(request.getAppointmentPhone())
                || !request.getAppointmentPhone().matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请填写正确的预约联系电话");
        }
    }
}

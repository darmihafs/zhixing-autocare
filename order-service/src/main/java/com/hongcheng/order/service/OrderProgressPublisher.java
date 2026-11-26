package com.hongcheng.order.service;

import com.hongcheng.common.enums.OrderStatusEnum;
import com.hongcheng.order.dto.OrderNotifyMessage;
import com.hongcheng.order.entity.Order;
import com.hongcheng.order.websocket.OrderWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 订单进度推送发布器：领域服务只描述状态变化，WebSocket 细节收敛在这里。
 * 推送失败不影响订单主流程，前端仍可通过详情接口兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProgressPublisher {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrderWebSocketHandler orderWebSocketHandler;

    public void publish(Order order, String type, Integer status, String message) {
        if (order == null) {
            return;
        }
        publish(order.getUserId(), order.getOrderNo(), status, type, message);
    }

    public void publish(Long userId, String orderNo, Integer status, String type, String message) {
        if (userId == null) {
            return;
        }
        OrderStatusEnum statusEnum = OrderStatusEnum.of(status);
        OrderNotifyMessage notify = new OrderNotifyMessage(
                type,
                orderNo,
                status,
                statusEnum == null ? null : statusEnum.getDesc(),
                message,
                LocalDateTime.now().format(TIME_FORMATTER));
        try {
            orderWebSocketHandler.push(userId, notify);
        } catch (Exception e) {
            log.warn("订单进度推送异常 orderNo={}, type={}", orderNo, type, e);
        }
    }
}

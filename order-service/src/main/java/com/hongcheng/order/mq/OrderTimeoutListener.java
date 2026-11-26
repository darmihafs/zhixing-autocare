package com.hongcheng.order.mq;

import com.hongcheng.order.dto.OrderTimeoutMessage;
import com.hongcheng.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 超时未支付延迟消息消费者：到点后 CAS 取消订单并回补库存。
 * <p>
 * 延迟由 broker 按 delayLevel 投递；订单若已支付/已取消，服务层直接忽略，
 * 重复投递也不会重复取消或重复回补。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = OrderMqConstants.TIMEOUT_TOPIC,
        consumerGroup = OrderMqConstants.TIMEOUT_CONSUMER_GROUP,
        consumeThreadNumber = 2)
public class OrderTimeoutListener implements RocketMQListener<OrderTimeoutMessage> {

    private final OrderService orderService;

    @Override
    public void onMessage(OrderTimeoutMessage message) {
        log.info("收到超时取消延迟消息 orderNo={}", message.getOrderNo());
        orderService.timeoutCancel(message.getOrderNo());
    }
}

package com.hongcheng.order.mq;

import com.hongcheng.order.dto.OrderCreateMessage;
import com.hongcheng.order.service.OrderCreateProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 下单削峰消费者：固定 8 个消费线程按下游（Feign + MySQL）能力处理排队请求。
 * <p>
 * 处理异常会抛出以触发 RocketMQ 重试（16 次后进入死信队列）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = OrderMqConstants.CREATE_TOPIC,
        consumerGroup = OrderMqConstants.CREATE_CONSUMER_GROUP,
        consumeThreadNumber = 8)
public class OrderCreateListener implements RocketMQListener<OrderCreateMessage> {

    private final OrderCreateProcessor orderCreateProcessor;

    @Override
    public void onMessage(OrderCreateMessage message) {
        log.info("收到下单消息 orderNo={}, userId={}", message.getOrderNo(), message.getUserId());
        orderCreateProcessor.process(message);
    }
}

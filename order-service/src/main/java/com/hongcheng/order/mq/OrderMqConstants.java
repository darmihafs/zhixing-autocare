package com.hongcheng.order.mq;

/**
 * 订单相关 MQ 主题与消费组常量。
 */
public final class OrderMqConstants {

    /** 下单削峰：API 只投递，消费者异步扣库存+建单 */
    public static final String CREATE_TOPIC = "HC_ORDER_CREATE";

    public static final String CREATE_CONSUMER_GROUP = "order-service-create-group";

    /** 延迟消息：待支付超时自动取消 */
    public static final String TIMEOUT_TOPIC = "HC_ORDER_TIMEOUT";

    public static final String TIMEOUT_CONSUMER_GROUP = "order-service-timeout-group";

    private OrderMqConstants() {
    }
}

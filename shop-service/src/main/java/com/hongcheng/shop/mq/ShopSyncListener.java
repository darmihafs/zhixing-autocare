package com.hongcheng.shop.mq;

import com.hongcheng.shop.dto.ShopSyncMessage;
import com.hongcheng.shop.service.EsIndexService;
import com.hongcheng.shop.service.ShopManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * ES 索引同步消费者：收到门店/服务变更消息后回查 DB 最新状态并覆盖写索引。
 * <p>
 * 幂等性：以业务 id 作为 ES 文档 _id 覆盖写入，重复消费无副作用；
 * 以 DB 最新状态为准（而非消息内容），并发/乱序消息也收敛到最终一致。
 * 抛异常时由 RocketMQ 自动重试（默认 16 次后进入死信队列）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = ShopManageService.ES_SYNC_TOPIC,
        selectorExpression = ShopManageService.TYPE_SHOP + " || " + ShopManageService.TYPE_SERVICE_ITEM,
        consumerGroup = "shop-service-es-sync-group",
        consumeThreadNumber = 4
)
public class ShopSyncListener implements RocketMQListener<ShopSyncMessage> {

    private final EsIndexService esIndexService;

    @Override
    public void onMessage(ShopSyncMessage message) {
        log.info("收到 ES 同步消息 type={}, id={}, ts={}", message.getType(), message.getId(), message.getTimestamp());
        if (ShopManageService.TYPE_SHOP.equals(message.getType())) {
            esIndexService.indexShop(message.getId());
        } else if (ShopManageService.TYPE_SERVICE_ITEM.equals(message.getType())) {
            esIndexService.indexServiceItem(message.getId());
        } else {
            log.warn("未知同步消息类型，忽略 type={}", message.getType());
        }
    }
}
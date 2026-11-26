package com.hongcheng.shop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 门店/服务变更同步 ES 的 MQ 消息体。
 * topic: HC_SHOP_SYNC，tag 即 type（SHOP / SERVICE_ITEM）。
 * 消费端收到后回查 DB 最新状态再覆盖写索引（幂等，天然收敛到最终一致）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopSyncMessage {

    /** SHOP / SERVICE_ITEM */
    private String type;

    private Long id;

    /** 发送时间戳（毫秒），用于排查同步延迟 */
    private Long timestamp;
}
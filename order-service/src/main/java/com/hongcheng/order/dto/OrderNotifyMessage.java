package com.hongcheng.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单进度 WebSocket 推送消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderNotifyMessage {

    /** ORDER_PAID / ORDER_DELIVERED / ORDER_COMPLETED / ORDER_CANCELLED / ORDER_TIMEOUT_CANCELLED */
    private String type;

    private String orderNo;

    private Integer status;

    private String statusDesc;

    private String message;

    private String timestamp;
}

package com.hongcheng.pay.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 支付结果推送消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayNotifyMessage {

    /** PAY_SUCCESS / PAY_FAILED */
    private String type;

    private String outTradeNo;

    private String orderNo;

    private java.math.BigDecimal amount;

    /** 支付流水状态 1成功 2失败 */
    private Integer status;

    /** yyyy-MM-dd HH:mm:ss，无则 null */
    private String payTime;

    private String message;
}

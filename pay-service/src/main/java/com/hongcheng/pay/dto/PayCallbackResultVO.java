package com.hongcheng.pay.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 支付回调处理结果。
 */
@Data
@AllArgsConstructor
public class PayCallbackResultVO {

    private String outTradeNo;

    private Integer status;

    private String statusDesc;

    /** true 表示本次为重复回调，未产生新的业务效果 */
    private boolean idempotent;
}

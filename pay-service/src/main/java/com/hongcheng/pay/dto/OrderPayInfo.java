package com.hongcheng.pay.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单服务内部查询返回（与 order-service OrderPayInfoVO 字段对齐）。
 */
@Data
public class OrderPayInfo {

    private String orderNo;

    private Long userId;

    private BigDecimal payAmount;

    /** 订单状态码，0待支付 10已支付 20服务中 30已完成 40已取消 */
    private Integer status;
}

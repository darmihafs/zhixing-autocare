package com.hongcheng.pay.dto;

import com.hongcheng.common.enums.PayStatusEnum;
import com.hongcheng.pay.entity.PayTransaction;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单视图。
 */
@Data
public class PayTransactionVO {

    private String outTradeNo;

    private String orderNo;

    private BigDecimal amount;

    private String channel;

    private Integer status;

    private String statusDesc;

    private String thirdTradeNo;

    private LocalDateTime payTime;

    private LocalDateTime createTime;

    public static PayTransactionVO from(PayTransaction transaction) {
        PayTransactionVO vo = new PayTransactionVO();
        vo.setOutTradeNo(transaction.getOutTradeNo());
        vo.setOrderNo(transaction.getOrderNo());
        vo.setAmount(transaction.getAmount());
        vo.setChannel(transaction.getChannel());
        vo.setStatus(transaction.getStatus());
        PayStatusEnum status = PayStatusEnum.of(transaction.getStatus());
        vo.setStatusDesc(status == null ? null : status.getDesc());
        vo.setThirdTradeNo(transaction.getThirdTradeNo());
        vo.setPayTime(transaction.getPayTime());
        vo.setCreateTime(transaction.getCreateTime());
        return vo;
    }
}

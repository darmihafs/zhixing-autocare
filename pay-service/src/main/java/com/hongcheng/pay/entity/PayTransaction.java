package com.hongcheng.pay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水（t_pay_transaction）。
 * <p>
 * out_trade_no 全局唯一，是支付回调的幂等键；状态只允许 0支付中 → 1成功/2失败/3已关闭。
 * 一单可多次发起支付，故 order_no 只有普通索引。
 */
@Data
@TableName("t_pay_transaction")
public class PayTransaction {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String outTradeNo;

    private String orderNo;

    private Long userId;

    private BigDecimal amount;

    /** MOCK/ALIPAY/WECHAT，当前仅模拟渠道 */
    private String channel;

    /** 0支付中 1成功 2失败 3已关闭，见 PayStatusEnum */
    private Integer status;

    private String thirdTradeNo;

    private LocalDateTime payTime;

    private LocalDateTime callbackTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

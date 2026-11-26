package com.hongcheng.pay.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 第三方支付回调请求（当前由 mock 渠道构造）。
 * <p>
 * 以 outTradeNo 定位流水；重复回调必须返回成功且不产生二次业务效果。
 */
@Data
public class PayCallbackRequest {

    @NotBlank(message = "商户支付流水号不能为空")
    private String outTradeNo;

    @NotBlank(message = "第三方交易号不能为空")
    private String thirdTradeNo;

    /** true 支付成功；false 支付失败 */
    @NotNull(message = "支付结果不能为空")
    private Boolean success;

    @NotNull(message = "回调金额不能为空")
    @DecimalMin(value = "0.01", message = "回调金额必须大于 0")
    private BigDecimal amount;
}

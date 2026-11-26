package com.hongcheng.pay.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建支付单请求。
 */
@Data
public class CreatePayRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;
}

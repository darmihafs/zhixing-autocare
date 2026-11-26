package com.hongcheng.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 下单请求（API 只做校验与入队，实际建单由 MQ 消费者完成）。
 */
@Data
public class CreateOrderRequest {

    /** 订单类型：1商品 2养车服务 */
    @NotNull(message = "订单类型不能为空")
    private Integer orderType;

    /** 门店ID（服务订单必填，商品订单可空） */
    private Long shopId;

    @NotNull(message = "订单明细不能为空")
    @Valid
    private List<OrderItemRequest> items;

    /** 服务预约到店时间（服务订单必填） */
    @Future(message = "预约时间必须晚于当前时间")
    private LocalDateTime appointmentTime;

    /** 服务预约联系人（服务订单必填） */
    @Size(max = 50, message = "联系人不能超过50字")
    private String appointmentContact;

    /** 服务预约联系电话（服务订单必填） */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确")
    private String appointmentPhone;

    @Size(max = 255, message = "备注不能超过255字")
    private String remark;
}

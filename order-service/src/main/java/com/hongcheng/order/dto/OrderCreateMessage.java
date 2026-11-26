package com.hongcheng.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 下单削峰消息体：API 入队后由消费者异步建单。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateMessage {

    private Long orderId;

    private String orderNo;

    private Long userId;

    private Long shopId;

    private Integer orderType;

    private List<OrderItemRequest> items;

    private LocalDateTime appointmentTime;

    private String appointmentContact;

    private String appointmentPhone;

    private String remark;
}

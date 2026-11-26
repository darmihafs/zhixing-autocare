package com.hongcheng.order.dto;

import com.hongcheng.common.enums.OrderStatusEnum;
import com.hongcheng.order.entity.Order;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单支付信息（内部接口返回，供 pay-service 校验归属/金额/状态）。
 */
@Data
public class OrderPayInfoVO {

    private String orderNo;

    private Long userId;

    private BigDecimal payAmount;

    /** 订单状态码，语义见 {@link OrderStatusEnum} */
    private Integer status;

    private LocalDateTime createTime;

    public static OrderPayInfoVO from(Order order) {
        OrderPayInfoVO vo = new OrderPayInfoVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setPayAmount(order.getPayAmount());
        vo.setStatus(order.getStatus());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }
}

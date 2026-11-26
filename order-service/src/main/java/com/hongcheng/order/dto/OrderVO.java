package com.hongcheng.order.dto;

import com.hongcheng.common.enums.OrderStatusEnum;
import com.hongcheng.common.enums.OrderTypeEnum;
import com.hongcheng.order.entity.Order;
import com.hongcheng.order.entity.OrderItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情视图（含明细快照与状态描述）。
 */
@Data
public class OrderVO {

    private Long id;

    private String orderNo;

    private Long userId;

    private Long shopId;

    private Integer orderType;

    private String orderTypeDesc;

    private Integer status;

    private String statusDesc;

    private BigDecimal totalAmount;

    private BigDecimal payAmount;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime finishTime;

    private LocalDateTime cancelTime;

    private String cancelReason;

    private LocalDateTime appointmentTime;

    private String appointmentContact;

    private String appointmentPhone;

    /** 支付截止时间：createTime + 配置的支付超时秒数（与 RocketMQ 延迟关单一致） */
    private LocalDateTime payDeadline;

    /** 服务端当前时间：前端据此计算倒计时偏移，避免本机时间不准 */
    private LocalDateTime serverTime;

    private String remark;

    private List<OrderItemVO> items;

    public static OrderVO from(Order order, List<OrderItem> items) {
        return from(order, items, 30L);
    }

    public static OrderVO from(Order order, List<OrderItem> items, long payTimeoutSeconds) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setShopId(order.getShopId());
        vo.setOrderType(order.getOrderType());
        OrderTypeEnum type = OrderTypeEnum.of(order.getOrderType());
        vo.setOrderTypeDesc(type == null ? "未知" : type.getDesc());
        vo.setStatus(order.getStatus());
        OrderStatusEnum status = OrderStatusEnum.of(order.getStatus());
        vo.setStatusDesc(status == null ? "未知" : status.getDesc());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());
        vo.setFinishTime(order.getFinishTime());
        vo.setCancelTime(order.getCancelTime());
        vo.setCancelReason(order.getCancelReason());
        vo.setAppointmentTime(order.getAppointmentTime());
        vo.setAppointmentContact(order.getAppointmentContact());
        vo.setAppointmentPhone(order.getAppointmentPhone());
        vo.setPayDeadline(order.getCreateTime() == null
                ? null : order.getCreateTime().plusSeconds(Math.max(1L, payTimeoutSeconds)));
        vo.setRemark(order.getRemark());
        vo.setItems(items.stream().map(OrderItemVO::from).toList());
        return vo;
    }
}

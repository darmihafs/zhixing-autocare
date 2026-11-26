package com.hongcheng.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态流转日志（t_order_status_log），状态机每次成功流转写一条。
 */
@Data
@TableName("t_order_status_log")
public class OrderStatusLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long orderId;

    private String orderNo;

    /** 原状态（创建时为空） */
    private Integer fromStatus;

    private Integer toStatus;

    /** CREATE/PAY/SERVICE/DELIVER/FINISH/CANCEL_TIMEOUT/CANCEL_USER/REFUND */
    private String event;

    /** 操作方：1用户 2系统 3商家 */
    private Integer operatorType;

    private String remark;

    private LocalDateTime createTime;
}

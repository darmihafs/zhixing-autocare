package com.hongcheng.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表实体（t_order）。
 */
@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务订单号（唯一索引），由雪花 ID 派生 */
    private String orderNo;

    private Long userId;

    /** 门店ID（服务订单必填，商品订单为空） */
    private Long shopId;

    /** 1商品 2养车服务 */
    private Integer orderType;

    private BigDecimal totalAmount;

    private BigDecimal payAmount;

    /** 状态机：0待支付 10已支付 20服务中/已发货 30已完成 40已取消 50退款中 60已退款 */
    private Integer status;

    private LocalDateTime payTime;

    private LocalDateTime finishTime;

    private LocalDateTime cancelTime;

    private String cancelReason;

    /** 服务预约到店时间（服务订单使用） */
    private LocalDateTime appointmentTime;

    /** 服务预约联系人 */
    private String appointmentContact;

    /** 服务预约联系电话 */
    private String appointmentPhone;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}

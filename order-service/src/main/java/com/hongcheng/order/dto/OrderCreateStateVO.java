package com.hongcheng.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 下单请求的处理状态（Redis 暂存，最多 TTL 30 分钟）。
 * <p>
 * state 取值：QUEUED 排队中 / CREATED 已建单 / FAILED 建单失败 / NOT_FOUND 无此请求。
 * userId 仅用于 Redis 暂存期间的归属校验，对外返回前会被清零，不暴露。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateStateVO {

    private String orderNo;

    private String state;

    private String message;

    private LocalDateTime updateTime;

    private Long userId;

    public OrderCreateStateVO(String orderNo, String state, String message, LocalDateTime updateTime) {
        this(orderNo, state, message, updateTime, null);
    }
}

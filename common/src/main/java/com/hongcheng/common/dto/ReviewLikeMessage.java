package com.hongcheng.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 评价点赞事件：user-service 在点赞/取消点赞成功后异步投递，
 * review-service 消费并原子增减 t_review.like_count。
 * <p>
 * eventId 用于消费端去重，抵消 RocketMQ at-least-once 投递带来的重复消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewLikeMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件唯一ID（消费端 Redis SETNX 去重） */
    private String eventId;

    /** 评价ID */
    private Long reviewId;

    /** +1 点赞 / -1 取消点赞 */
    private Integer delta;

    /** 操作用户ID */
    private Long userId;

    /** 事件时间戳 */
    private Long timestamp;
}

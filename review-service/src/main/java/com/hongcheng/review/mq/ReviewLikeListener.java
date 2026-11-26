package com.hongcheng.review.mq;

import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.dto.ReviewLikeMessage;
import com.hongcheng.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 评价点赞事件消费者：按 eventId 去重，再原子增减 t_review.like_count。
 * <p>
 * 去重键保留 24h，覆盖 RocketMQ 重试窗口；评价不存在/已删除时删除去重键，
 * 便于后续补偿消息重新投递（虽然当前没有重放入口）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = CommonConstants.TOPIC_REVIEW_LIKE,
        consumerGroup = "review-service-like-sync-group",
        consumeThreadNumber = 4)
public class ReviewLikeListener implements RocketMQListener<ReviewLikeMessage> {

    private static final String EVENT_KEY_PREFIX = "review:like:event:";
    private static final Duration EVENT_TTL = Duration.ofDays(1);

    private final ReviewMapper reviewMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(ReviewLikeMessage message) {
        if (message == null || message.getReviewId() == null || message.getDelta() == null) {
            log.warn("评价点赞事件参数不完整，忽略 message={}", message);
            return;
        }
        String eventKey = EVENT_KEY_PREFIX + (message.getEventId() != null
                ? message.getEventId()
                : message.getReviewId() + ":" + message.getDelta() + ":" + message.getTimestamp());
        Boolean first = redisTemplate.opsForValue().setIfAbsent(eventKey, "1", EVENT_TTL);
        if (!Boolean.TRUE.equals(first)) {
            log.info("评价点赞事件重复投递，已跳过 eventId={}", message.getEventId());
            return;
        }
        int rows = reviewMapper.adjustLikeCount(message.getReviewId(), message.getDelta());
        if (rows == 0) {
            redisTemplate.delete(eventKey);
            log.warn("评价点赞事件未命中评价 reviewId={}, delta={}",
                    message.getReviewId(), message.getDelta());
            return;
        }
        log.info("评价点赞数同步成功 reviewId={}, delta={}, userId={}",
                message.getReviewId(), message.getDelta(), message.getUserId());
    }
}

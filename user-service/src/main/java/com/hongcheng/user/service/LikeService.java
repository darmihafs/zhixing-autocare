package com.hongcheng.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.dto.ReviewLikeMessage;
import com.hongcheng.common.enums.LikeTargetTypeEnum;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.user.entity.UserLike;
import com.hongcheng.user.mapper.UserLikeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 点赞/取消点赞/点赞列表/是否已点赞。
 * <p>
 * 三类目标（评价/服务项目/商品）均在其他服务库，存在性校验待 Feign 接入后补充；
 * 重复点赞由 uk_user_target 唯一索引兜底转业务码；取消为物理删除。
 * 评价点赞成功后异步投递 HC_REVIEW_LIKE，review-service 消费并原子更新 like_count；
 * MQ 失败不回滚点赞主流程（最终一致，管理端可触发重建补偿）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final UserLikeMapper userLikeMapper;
    private final RocketMQTemplate rocketMQTemplate;

    public void like(Long userId, Integer targetType, Long targetId) {
        LikeTargetTypeEnum type = requireType(targetType);
        UserLike like = new UserLike();
        like.setUserId(userId);
        like.setTargetType(targetType);
        like.setTargetId(targetId);
        try {
            userLikeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.ALREADY_LIKED);
        }
        if (type == LikeTargetTypeEnum.REVIEW) {
            publishLikeEvent(targetId, 1, userId);
        }
        log.info("点赞成功 userId={}, targetType={}, targetId={}", userId, targetType, targetId);
    }

    public void unlike(Long userId, Integer targetType, Long targetId) {
        LikeTargetTypeEnum type = requireType(targetType);
        int rows = userLikeMapper.delete(new LambdaQueryWrapper<UserLike>()
                .eq(UserLike::getUserId, userId)
                .eq(UserLike::getTargetType, targetType)
                .eq(UserLike::getTargetId, targetId));
        if (rows == 0) {
            throw new BusinessException(ResultCode.NOT_LIKED);
        }
        if (type == LikeTargetTypeEnum.REVIEW) {
            publishLikeEvent(targetId, -1, userId);
        }
        log.info("取消点赞成功 userId={}, targetType={}, targetId={}", userId, targetType, targetId);
    }

    /** 我点赞的目标ID列表（按点赞时间倒序） */
    public List<Long> listTargets(Long userId, Integer targetType) {
        requireType(targetType);
        return userLikeMapper.selectList(new LambdaQueryWrapper<UserLike>()
                        .eq(UserLike::getUserId, userId)
                        .eq(UserLike::getTargetType, targetType)
                        .orderByDesc(UserLike::getCreateTime))
                .stream()
                .map(UserLike::getTargetId)
                .toList();
    }

    public boolean isLiked(Long userId, Integer targetType, Long targetId) {
        requireType(targetType);
        return userLikeMapper.selectCount(new LambdaQueryWrapper<UserLike>()
                .eq(UserLike::getUserId, userId)
                .eq(UserLike::getTargetType, targetType)
                .eq(UserLike::getTargetId, targetId)) > 0;
    }

    /**
     * 异步投递评价点赞事件；MQ 不可用时只记日志，不影响已成功的点赞/取消操作。
     * eventId 供消费端去重，抵消 RocketMQ at-least-once 重复投递。
     */
    private void publishLikeEvent(Long reviewId, int delta, Long userId) {
        ReviewLikeMessage event = new ReviewLikeMessage(
                UUID.randomUUID().toString(), reviewId, delta, userId, System.currentTimeMillis());
        try {
            rocketMQTemplate.asyncSend(CommonConstants.TOPIC_REVIEW_LIKE, event, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("评价点赞事件发送成功 reviewId={}, delta={}, msgId={}",
                            reviewId, delta, sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("评价点赞事件发送失败 reviewId={}, delta={}", reviewId, delta, e);
                }
            });
        } catch (Exception e) {
            log.error("评价点赞事件投递异常 reviewId={}, delta={}", reviewId, delta, e);
        }
    }

    private LikeTargetTypeEnum requireType(Integer targetType) {
        LikeTargetTypeEnum type = LikeTargetTypeEnum.of(targetType);
        if (type == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的点赞类型: " + targetType);
        }
        return type;
    }
}
package com.hongcheng.user.service;

import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.dto.ReviewLikeMessage;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.user.entity.UserLike;
import com.hongcheng.user.mapper.UserLikeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LikeService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private UserLikeMapper userLikeMapper;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @InjectMocks
    private LikeService likeService;

    @Test
    @DisplayName("点赞成功 - 落库 userId/targetType/targetId 正确")
    void like_success_persistsFields() {
        // given
        when(userLikeMapper.insert(any(UserLike.class))).thenReturn(1);

        // when
        likeService.like(7L, 3, 5L);

        // then
        ArgumentCaptor<UserLike> captor = ArgumentCaptor.forClass(UserLike.class);
        verify(userLikeMapper).insert(captor.capture());
        UserLike saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getTargetType()).isEqualTo(3);
        assertThat(saved.getTargetId()).isEqualTo(5L);
        // 非评价目标不投递评价点赞事件
        verify(rocketMQTemplate, never()).asyncSend(any(String.class), any(Object.class), any(SendCallback.class));
    }

    @Test
    @DisplayName("评价点赞 - 成功后投递 HC_REVIEW_LIKE 事件（delta=+1）")
    void like_review_publishesLikeEvent() {
        when(userLikeMapper.insert(any(UserLike.class))).thenReturn(1);

        likeService.like(7L, 1, 5L);

        ArgumentCaptor<ReviewLikeMessage> captor = ArgumentCaptor.forClass(ReviewLikeMessage.class);
        verify(rocketMQTemplate).asyncSend(eq(CommonConstants.TOPIC_REVIEW_LIKE), captor.capture(), any(SendCallback.class));
        ReviewLikeMessage event = captor.getValue();
        assertThat(event.getReviewId()).isEqualTo(5L);
        assertThat(event.getDelta()).isEqualTo(1);
        assertThat(event.getUserId()).isEqualTo(7L);
        assertThat(event.getEventId()).isNotBlank();
    }

    @Test
    @DisplayName("取消评价点赞 - 成功后投递 HC_REVIEW_LIKE 事件（delta=-1）")
    void unlike_review_publishesLikeEvent() {
        when(userLikeMapper.delete(any())).thenReturn(1);

        likeService.unlike(7L, 1, 5L);

        ArgumentCaptor<ReviewLikeMessage> captor = ArgumentCaptor.forClass(ReviewLikeMessage.class);
        verify(rocketMQTemplate).asyncSend(eq(CommonConstants.TOPIC_REVIEW_LIKE), captor.capture(), any(SendCallback.class));
        assertThat(captor.getValue().getDelta()).isEqualTo(-1);
        assertThat(captor.getValue().getReviewId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("重复点赞 - 唯一索引冲突转 1007")
    void like_duplicate_throws1007() {
        // given
        when(userLikeMapper.insert(any(UserLike.class)))
                .thenThrow(new DuplicateKeyException("uk_user_target"));

        // when / then
        assertThatThrownBy(() -> likeService.like(7L, 3, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.ALREADY_LIKED.getCode());
    }

    @Test
    @DisplayName("取消未点赞 - 抛 1008")
    void unlike_notLiked_throws1008() {
        // given
        when(userLikeMapper.delete(any())).thenReturn(0);

        // when / then
        assertThatThrownBy(() -> likeService.unlike(7L, 3, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.NOT_LIKED.getCode());
    }

    @Test
    @DisplayName("非法点赞类型 - 抛 400")
    void like_invalidType_throws400() {
        // when / then
        assertThatThrownBy(() -> likeService.like(7L, 9, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("是否已点赞 - count=0 为 false")
    void isLiked_countZero_returnsFalse() {
        // given
        when(userLikeMapper.selectCount(any())).thenReturn(0L);

        // when / then
        assertThat(likeService.isLiked(7L, 1, 5L)).isFalse();
    }
}
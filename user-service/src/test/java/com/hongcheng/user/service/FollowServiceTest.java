package com.hongcheng.user.service;

import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.user.entity.User;
import com.hongcheng.user.entity.UserFollow;
import com.hongcheng.user.mapper.UserFollowMapper;
import com.hongcheng.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FollowService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private UserFollowMapper userFollowMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private FollowService followService;

    @Test
    @DisplayName("关注自己 - 抛 1009 且不落库")
    void follow_self_throws1009() {
        // when / then
        assertThatThrownBy(() -> followService.follow(7L, 2, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.CANNOT_FOLLOW_SELF.getCode());
        verify(userFollowMapper, never()).insert(any(UserFollow.class));
    }

    @Test
    @DisplayName("关注不存在的用户 - 抛 404")
    void follow_missingUserTarget_throws404() {
        // given
        when(userMapper.selectById(999L)).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> followService.follow(7L, 2, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("重复关注 - 唯一索引冲突转 1005")
    void follow_duplicate_throws1005() {
        // given
        when(userFollowMapper.insert(any(UserFollow.class)))
                .thenThrow(new DuplicateKeyException("uk_user_target"));

        // when / then
        assertThatThrownBy(() -> followService.follow(7L, 1, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.ALREADY_FOLLOWED.getCode());
    }

    @Test
    @DisplayName("关注门店成功 - 落库字段正确")
    void follow_shop_success() {
        // given
        when(userFollowMapper.insert(any(UserFollow.class))).thenReturn(1);

        // when
        followService.follow(7L, 1, 3L);

        // then
        verify(userFollowMapper).insert(any(UserFollow.class));
    }

    @Test
    @DisplayName("取关未关注 - 抛 1006")
    void unfollow_notFollowed_throws1006() {
        // given
        when(userFollowMapper.delete(any())).thenReturn(0);

        // when / then
        assertThatThrownBy(() -> followService.unfollow(7L, 1, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.NOT_FOLLOWED.getCode());
    }

    @Test
    @DisplayName("非法关注类型 - 抛 400")
    void follow_invalidType_throws400() {
        // when / then
        assertThatThrownBy(() -> followService.follow(7L, 9, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ResultCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("是否已关注 - count>0 为 true")
    void isFollowed_countPositive_returnsTrue() {
        // given
        when(userFollowMapper.selectCount(any())).thenReturn(1L);

        // when / then
        assertThat(followService.isFollowed(7L, 1, 1L)).isTrue();
    }

    @Test
    @DisplayName("关注用户目标存在 - 校验通过后落库")
    void follow_existingUserTarget_success() {
        // given
        User target = new User();
        target.setId(8L);
        when(userMapper.selectById(8L)).thenReturn(target);
        when(userFollowMapper.insert(any(UserFollow.class))).thenReturn(1);

        // when
        followService.follow(7L, 2, 8L);

        // then
        verify(userFollowMapper).insert(any(UserFollow.class));
    }
}
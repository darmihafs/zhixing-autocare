package com.hongcheng.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hongcheng.common.enums.FollowTargetTypeEnum;
import com.hongcheng.common.exception.BusinessException;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.user.entity.UserFollow;
import com.hongcheng.user.mapper.UserFollowMapper;
import com.hongcheng.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 关注/取关/关注列表/是否已关注。
 * <p>
 * 重复关注由 uk_user_target 唯一索引兜底转业务码；取关为物理删除。
 * 门店目标的存在性校验需 shop-service，待 Feign 接入后补充；
 * 用户目标在本库直接校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

    private final UserFollowMapper userFollowMapper;
    private final UserMapper userMapper;

    public void follow(Long userId, Integer targetType, Long targetId) {
        FollowTargetTypeEnum type = requireType(targetType);
        if (type == FollowTargetTypeEnum.USER) {
            if (targetId.equals(userId)) {
                throw new BusinessException(ResultCode.CANNOT_FOLLOW_SELF);
            }
            if (userMapper.selectById(targetId) == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "关注目标不存在");
            }
        }
        UserFollow follow = new UserFollow();
        follow.setUserId(userId);
        follow.setTargetType(targetType);
        follow.setTargetId(targetId);
        try {
            userFollowMapper.insert(follow);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.ALREADY_FOLLOWED);
        }
        log.info("关注成功 userId={}, targetType={}, targetId={}", userId, targetType, targetId);
    }

    public void unfollow(Long userId, Integer targetType, Long targetId) {
        requireType(targetType);
        int rows = userFollowMapper.delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getUserId, userId)
                .eq(UserFollow::getTargetType, targetType)
                .eq(UserFollow::getTargetId, targetId));
        if (rows == 0) {
            throw new BusinessException(ResultCode.NOT_FOLLOWED);
        }
        log.info("取关成功 userId={}, targetType={}, targetId={}", userId, targetType, targetId);
    }

    /** 我关注的目标ID列表（按关注时间倒序） */
    public List<Long> listTargets(Long userId, Integer targetType) {
        requireType(targetType);
        return userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getUserId, userId)
                        .eq(UserFollow::getTargetType, targetType)
                        .orderByDesc(UserFollow::getCreateTime))
                .stream()
                .map(UserFollow::getTargetId)
                .toList();
    }

    public boolean isFollowed(Long userId, Integer targetType, Long targetId) {
        requireType(targetType);
        return userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getUserId, userId)
                .eq(UserFollow::getTargetType, targetType)
                .eq(UserFollow::getTargetId, targetId)) > 0;
    }

    private FollowTargetTypeEnum requireType(Integer targetType) {
        FollowTargetTypeEnum type = FollowTargetTypeEnum.of(targetType);
        if (type == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的关注类型: " + targetType);
        }
        return type;
    }
}
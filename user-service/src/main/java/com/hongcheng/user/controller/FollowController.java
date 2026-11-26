package com.hongcheng.user.controller;

import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.result.Result;
import com.hongcheng.user.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 关注接口（需登录）。targetType：1门店 2用户。
 */
@RestController
@RequestMapping("/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{targetType}/{targetId}")
    public Result<Void> follow(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                               @PathVariable Integer targetType,
                               @PathVariable Long targetId) {
        followService.follow(userId, targetType, targetId);
        return Result.ok();
    }

    @DeleteMapping("/{targetType}/{targetId}")
    public Result<Void> unfollow(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                 @PathVariable Integer targetType,
                                 @PathVariable Long targetId) {
        followService.unfollow(userId, targetType, targetId);
        return Result.ok();
    }

    @GetMapping("/list")
    public Result<List<Long>> list(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                   @RequestParam Integer targetType) {
        return Result.ok(followService.listTargets(userId, targetType));
    }

    @GetMapping("/status")
    public Result<Boolean> status(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                  @RequestParam Integer targetType,
                                  @RequestParam Long targetId) {
        return Result.ok(followService.isFollowed(userId, targetType, targetId));
    }
}
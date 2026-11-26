package com.hongcheng.user.controller;

import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.result.Result;
import com.hongcheng.user.service.LikeService;
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
 * 点赞接口（需登录）。targetType：1评价 2服务项目 3商品。
 */
@RestController
@RequestMapping("/like")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/{targetType}/{targetId}")
    public Result<Void> like(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                             @PathVariable Integer targetType,
                             @PathVariable Long targetId) {
        likeService.like(userId, targetType, targetId);
        return Result.ok();
    }

    @DeleteMapping("/{targetType}/{targetId}")
    public Result<Void> unlike(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                               @PathVariable Integer targetType,
                               @PathVariable Long targetId) {
        likeService.unlike(userId, targetType, targetId);
        return Result.ok();
    }

    @GetMapping("/list")
    public Result<List<Long>> list(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                   @RequestParam Integer targetType) {
        return Result.ok(likeService.listTargets(userId, targetType));
    }

    @GetMapping("/status")
    public Result<Boolean> status(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                  @RequestParam Integer targetType,
                                  @RequestParam Long targetId) {
        return Result.ok(likeService.isLiked(userId, targetType, targetId));
    }
}
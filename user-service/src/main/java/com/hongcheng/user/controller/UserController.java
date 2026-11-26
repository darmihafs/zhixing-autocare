package com.hongcheng.user.controller;

import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.result.Result;
import com.hongcheng.user.dto.UpdateProfileRequest;
import com.hongcheng.user.dto.UserVO;
import com.hongcheng.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户信息接口（需登录）。
 * <p>
 * 用户ID只信网关透传的 X-User-Id 头（AuthHeaderInterceptor 已兜底校验），
 * 不接受客户端自报 userId 参数，防越权。
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public Result<UserVO> me(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId) {
        return Result.ok(userService.getById(userId));
    }

    @PutMapping("/me")
    public Result<UserVO> updateProfile(@RequestHeader(CommonConstants.HEADER_USER_ID) Long userId,
                                        @Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok(userService.updateProfile(userId, request));
    }
}
package com.hongcheng.user.controller;

import com.hongcheng.common.result.Result;
import com.hongcheng.user.dto.LoginRequest;
import com.hongcheng.user.dto.LoginVO;
import com.hongcheng.user.dto.RegisterRequest;
import com.hongcheng.user.dto.UserVO;
import com.hongcheng.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（网关白名单，无需 Token）。
 * <p>
 * 对外路径经网关 /api/auth/**  StripPrefix 后到达此处。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(userService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request));
    }
}
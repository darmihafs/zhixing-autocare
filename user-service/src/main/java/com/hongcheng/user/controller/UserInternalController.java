package com.hongcheng.user.controller;

import com.hongcheng.common.result.Result;
import com.hongcheng.user.dto.UserBriefVO;
import com.hongcheng.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户内部接口（/internal/** 不在网关路由，外部不可达；由 InternalTokenInterceptor 校验 X-Internal-Token）。
 * <p>
 * 仅服务间 Feign 调用：管理端订单/评价列表补全真实用户名与昵称。
 */
@RestController
@RequestMapping("/internal/user")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserService userService;

    /** 批量用户简要：ids 为逗号分隔的用户ID，单次上限 50 */
    @GetMapping("/briefs")
    public Result<List<UserBriefVO>> briefs(@RequestParam("ids") String ids) {
        return Result.ok(userService.listBriefs(ids));
    }
}
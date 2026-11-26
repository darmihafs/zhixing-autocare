package com.hongcheng.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.result.Result;
import com.hongcheng.user.dto.ImpersonationVO;
import com.hongcheng.user.dto.UserAdminVO;
import com.hongcheng.user.service.UserManageService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户管理与模拟视角接口。
 * <p>
 * /user/manage/** 由 AdminRoleInterceptor 校验仅 ADMIN（不接受 X-Admin-Token）；
 * /user/impersonation/exit 仅需登录态，但是否处于模拟态由网关透传的
 * X-Impersonator-Id 判定（客户端伪造值已被网关剥离）。
 */
@Validated
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserManageController {

    private final UserManageService userManageService;

    /** 管理端用户分页：真实 username/nickname/userId + 打码手机号 */
    @GetMapping("/manage/page")
    public Result<Page<UserAdminVO>> page(@RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
                                          @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer pageSize) {
        return Result.ok(userManageService.pageForAdmin(keyword, pageNum, pageSize));
    }

    /** 切换为该用户视角：仅 ADMIN 可调用；目标须为启用的普通用户且非自身 */
    @PostMapping("/manage/{id}/impersonate")
    public Result<ImpersonationVO> impersonate(@RequestHeader(CommonConstants.HEADER_USER_ID) Long adminId,
                                               @RequestHeader(CommonConstants.HEADER_USERNAME) String adminUsername,
                                               @PathVariable Long id) {
        return Result.ok(userManageService.impersonate(adminId, adminUsername, id));
    }

    /** 退出模拟：写审计日志；前端随后恢复原管理员 Token，无需重新登录 */
    @PostMapping("/impersonation/exit")
    public Result<Void> exit(@RequestHeader(value = CommonConstants.HEADER_IMPERSONATOR_ID, required = false) Long impersonatorId,
                             @RequestHeader(CommonConstants.HEADER_USER_ID) Long targetId) {
        userManageService.exitImpersonation(impersonatorId, targetId);
        return Result.ok();
    }
}
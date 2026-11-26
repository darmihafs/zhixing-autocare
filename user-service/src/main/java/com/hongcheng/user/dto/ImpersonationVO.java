package com.hongcheng.user.dto;

import lombok.Data;

/**
 * 管理员模拟用户视角（Impersonation）签发结果。
 * <p>
 * token 为短效模拟 Token（role=USER + impersonatorId/impersonatedBy 声明），
 * 不伴随 Refresh Token；impersonatorId/impersonatedBy 同时返回供前端审计展示与恢复身份。
 */
@Data
public class ImpersonationVO {

    /** 模拟 Token（短有效期） */
    private String token;

    /** 有效期秒数 */
    private Long expiresIn;

    /** 被模拟的目标用户视图（role=USER） */
    private UserVO user;

    /** 发起模拟的管理员ID */
    private Long impersonatorId;

    /** 发起模拟的管理员用户名 */
    private String impersonatedBy;
}
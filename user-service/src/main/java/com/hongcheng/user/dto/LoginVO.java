package com.hongcheng.user.dto;

import lombok.Data;

/**
 * 登录成功返回：Token + 用户信息。
 */
@Data
public class LoginVO {

    /** JWT，前端后续以 Authorization: Bearer <token> 携带 */
    private String token;

    /** 有效期（秒） */
    private Long expiresIn;

    /** 用户角色（ADMIN/USER）：与 JWT role 声明、user.role 一致，前端据此分流跳转 */
    private String role;

    private UserVO user;
}
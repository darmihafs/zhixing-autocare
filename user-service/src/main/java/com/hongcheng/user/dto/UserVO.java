package com.hongcheng.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息视图（不含密码）。
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String phone;

    private String avatar;

    private Integer gender;

    /** 角色：USER普通用户 ADMIN管理员 */
    private String role;

    private LocalDateTime createTime;
}
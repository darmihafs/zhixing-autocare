package com.hongcheng.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表 t_user。
 * <p>
 * create_time/update_time 由数据库默认值维护，插入时不带（MP 默认非 null 才拼列）。
 */
@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    /** BCrypt 哈希，绝不返回前端 */
    private String password;

    private String nickname;

    private String phone;

    private String avatar;

    /** 0未知 1男 2女 */
    private Integer gender;

    /** 1正常 0禁用 */
    private Integer status;

    /** 角色：USER普通用户 ADMIN管理员 */
    private String role;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
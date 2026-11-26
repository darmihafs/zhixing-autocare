package com.hongcheng.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户关注表 t_user_follow（无逻辑删除列，取关为物理删除）。
 */
@Data
@TableName("t_user_follow")
public class UserFollow {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 1门店 2用户，见 FollowTargetTypeEnum */
    private Integer targetType;

    private Long targetId;

    private LocalDateTime createTime;
}
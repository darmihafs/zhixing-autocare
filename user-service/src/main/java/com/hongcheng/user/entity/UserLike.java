package com.hongcheng.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户点赞表 t_user_like（无逻辑删除列，取消点赞为物理删除）。
 */
@Data
@TableName("t_user_like")
public class UserLike {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 1评价 2服务项目 3商品，见 LikeTargetTypeEnum */
    private Integer targetType;

    private Long targetId;

    private LocalDateTime createTime;
}
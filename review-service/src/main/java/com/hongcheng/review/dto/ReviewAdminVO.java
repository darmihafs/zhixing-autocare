package com.hongcheng.review.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.BeanUtils;

/**
 * 管理端评价视图：在 ReviewVO 基础上补全真实 username/nickname（来自用户内部简要接口）。
 * <p>
 * C 端公开评价接口仍只返回 userId，前台匿名/脱敏展示不变。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewAdminVO extends ReviewVO {

    /** 真实用户名（管理端可见，不再使用「匿名用户/用户+数字」） */
    private String username;

    /** 真实昵称 */
    private String nickname;

    public static ReviewAdminVO from(ReviewVO vo, UserBriefVO user) {
        ReviewAdminVO admin = new ReviewAdminVO();
        BeanUtils.copyProperties(vo, admin);
        if (user != null) {
            admin.setUsername(user.getUsername());
            admin.setNickname(user.getNickname());
        }
        return admin;
    }
}
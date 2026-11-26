package com.hongcheng.review.dto;

import lombok.Data;

/**
 * 用户简要（user-service /internal/user/briefs 返回体的本服务映射）。
 * <p>
 * 仅 id/username/nickname 非敏感字段，用于管理端评价列表展示真实用户名。
 */
@Data
public class UserBriefVO {

    private Long id;

    private String username;

    private String nickname;
}
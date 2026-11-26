package com.hongcheng.user.dto;

import com.hongcheng.user.entity.User;
import lombok.Data;

/**
 * 内部用户简要视图：供管理端跨服务补全真实用户名/昵称。
 * <p>
 * 仅含非敏感字段（id/username/nickname），手机号等敏感字段一律不出内部接口。
 */
@Data
public class UserBriefVO {

    private Long id;

    private String username;

    private String nickname;

    public static UserBriefVO from(User user) {
        UserBriefVO vo = new UserBriefVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        return vo;
    }
}
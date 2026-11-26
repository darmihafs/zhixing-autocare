package com.hongcheng.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 关注目标类型（与 t_user_follow.target_type 注释一致）。
 */
@Getter
@AllArgsConstructor
public enum FollowTargetTypeEnum {

    SHOP(1, "门店"),
    USER(2, "用户");

    private final int code;
    private final String desc;

    /** 按 code 解析，未知 code 返回 null */
    public static FollowTargetTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (FollowTargetTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
package com.hongcheng.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 点赞目标类型（与 t_user_like.target_type 注释一致）。
 */
@Getter
@AllArgsConstructor
public enum LikeTargetTypeEnum {

    REVIEW(1, "评价"),
    SERVICE_ITEM(2, "服务项目"),
    PRODUCT(3, "商品");

    private final int code;
    private final String desc;

    /** 按 code 解析，未知 code 返回 null */
    public static LikeTargetTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (LikeTargetTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
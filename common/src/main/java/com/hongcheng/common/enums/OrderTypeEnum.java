package com.hongcheng.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单类型（与 t_order.order_type、t_order_item.item_type 一致）。
 */
@Getter
@AllArgsConstructor
public enum OrderTypeEnum {

    PRODUCT(1, "商品订单"),
    SERVICE(2, "养车服务订单");

    private final int code;
    private final String desc;

    /** 按 code 解析，未知 code 返回 null */
    public static OrderTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
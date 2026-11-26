package com.hongcheng.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 支付流水状态（与 t_pay_transaction.status 注释一致）。
 * <p>
 * 0支付中 → 1成功 / 2失败；0支付中 → 3已关闭（订单超时取消联动）。
 * 回调幂等以 out_trade_no 唯一索引兜底，状态只允许 0 → 1/2/3 单向流转。
 */
@Getter
@AllArgsConstructor
public enum PayStatusEnum {

    PAYING(0, "支付中"),
    SUCCESS(1, "支付成功"),
    FAILED(2, "支付失败"),
    CLOSED(3, "已关闭");

    private final int code;
    private final String desc;

    /** 按 code 解析，未知 code 返回 null */
    public static PayStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (PayStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
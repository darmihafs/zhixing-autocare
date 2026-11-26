package com.hongcheng.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一返回码。
 * <p>
 * 200/4xx/5xx 与 HTTP 语义对齐；1xxx 起为业务错误码，按服务分段：
 * 1000~1099 用户、1100~1199 商品、1200~1299 门店、
 * 1300~1399 订单、1400~1499 支付、1500~1599 评价。
 * 业务码随各服务开发逐步补充，本阶段只定义通用码。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    // ---- 用户服务 1000~1099 ----
    USERNAME_EXISTS(1001, "用户名已被注册"),
    PHONE_EXISTS(1002, "手机号已被注册"),
    LOGIN_FAILED(1003, "用户名或密码错误"),
    USER_DISABLED(1004, "账号已被禁用，请联系管理员"),
    ALREADY_FOLLOWED(1005, "已关注，请勿重复操作"),
    NOT_FOLLOWED(1006, "尚未关注"),
    ALREADY_LIKED(1007, "已点赞，请勿重复操作"),
    NOT_LIKED(1008, "尚未点赞"),
    CANNOT_FOLLOW_SELF(1009, "不能关注自己"),

    // ---- 商品服务 1100~1199 ----
    STOCK_NOT_ENOUGH(1101, "库存不足"),
    PRODUCT_NOT_FOUND(1102, "商品不存在或已下架"),
    CATEGORY_NOT_FOUND(1103, "分类不存在"),
    STOCK_RESTORE_FAILED(1104, "库存回补失败"),
    CART_ITEM_NOT_FOUND(1105, "购物车条目不存在"),
    CART_SELECTION_EMPTY(1106, "请先选择要结算的商品"),

    // ---- 门店服务 1200~1299 ----
    SHOP_NOT_FOUND(1201, "门店不存在"),
    SERVICE_ITEM_NOT_FOUND(1202, "服务项目不存在"),

    // ---- 订单服务 1300~1399 ----
    ORDER_NOT_FOUND(1301, "订单不存在"),
    ORDER_STATUS_INVALID(1302, "订单当前状态不允许该操作"),
    ORDER_ACCESS_DENIED(1303, "无权操作该订单"),
    ORDER_CREATING(1304, "订单创建中，请稍后查询"),
    ORDER_CREATE_FAILED(1305, "订单创建失败"),
    ORDER_SUBMIT_FAILED(1306, "下单请求提交失败，请重试"),
    ORDER_TYPE_UNSUPPORTED(1307, "暂不支持该订单类型"),
    ORDER_RATE_LIMITED(1308, "下单请求过于频繁，请稍后重试"),

    // ---- 支付服务 1400~1499 ----
    PAY_NOT_FOUND(1401, "支付单不存在"),
    PAY_STATUS_INVALID(1402, "支付单当前状态不允许该操作"),
    PAY_AMOUNT_MISMATCH(1403, "支付金额与支付单不一致"),
    PAY_ORDER_NOT_PAYABLE(1404, "订单当前不可支付"),
    PAY_ACCESS_DENIED(1405, "无权操作该支付单"),
    PAY_RATE_LIMITED(1406, "支付请求过于频繁，请稍后重试"),

    // ---- 评价服务 1500~1599 ----
    REVIEW_NOT_FOUND(1501, "评价不存在"),
    REVIEW_ALREADY_EXISTS(1502, "该订单商品已评价，请勿重复提交"),
    REVIEW_ORDER_NOT_COMPLETED(1503, "仅已完成订单可评价"),
    REVIEW_ITEM_NOT_IN_ORDER(1504, "评价商品不在该订单中"),
    REVIEW_ACCESS_DENIED(1505, "无权评价该订单"),

    INTERNAL_ERROR(500, "系统繁忙，请稍后重试"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用");

    private final int code;
    private final String message;
}
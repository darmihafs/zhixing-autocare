package com.hongcheng.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回体。
 * <p>
 * 约定：HTTP 状态码恒为 200，业务结果由 {@code code} 字段表达，
 * 前端 axios 拦截器只需判断 {@code code == 200}。
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 返回码，200 表示成功，见 {@link ResultCode} */
    private int code;

    /** 提示信息 */
    private String message;

    /** 业务数据，失败时为 null */
    private T data;

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return fail(resultCode.getCode(), resultCode.getMessage());
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /** 是否成功 */
    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
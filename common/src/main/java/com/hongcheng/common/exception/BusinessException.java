package com.hongcheng.common.exception;

import com.hongcheng.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：业务规则不满足时抛出，由全局异常处理器转为统一返回体。
 * <p>
 * 不携带堆栈（业务异常属预期分支，无需 stackTrace，减少日志噪音与开销）。
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务返回码 */
    private final int code;

    public BusinessException(ResultCode resultCode) {
        this(resultCode.getCode(), resultCode.getMessage());
    }

    public BusinessException(ResultCode resultCode, String message) {
        this(resultCode.getCode(), message);
    }

    public BusinessException(int code, String message) {
        super(message, null, false, false);
        this.code = code;
    }
}
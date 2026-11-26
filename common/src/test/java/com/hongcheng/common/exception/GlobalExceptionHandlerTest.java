package com.hongcheng.common.exception;

import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler 直调测试（不启 Spring 上下文）。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("业务异常 - 原样透传业务码与提示")
    void handleBusinessException_keepsCodeAndMessage() {
        // given
        BusinessException e = new BusinessException(1301, "订单状态不允许取消");

        // when
        Result<Void> result = handler.handleBusinessException(e);

        // then
        assertThat(result.getCode()).isEqualTo(1301);
        assertThat(result.getMessage()).isEqualTo("订单状态不允许取消");
    }

    @Test
    @DisplayName("无对应资源 - 归 404 而非兜底 500")
    void handleNoResourceFound_returns404() {
        // given
        NoResourceFoundException e = new NoResourceFoundException(HttpMethod.GET, "not-exist");

        // when
        Result<Void> result = handler.handleNoResourceFound(e);

        // then
        assertThat(result.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("未预期异常 - 兜底 500 且不泄露内部信息")
    void handleException_returns500WithGenericMessage() {
        // when
        Result<Void> result = handler.handleException(new IllegalStateException("db password=xxx"));

        // then
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).isEqualTo("系统繁忙，请稍后重试");
    }
}
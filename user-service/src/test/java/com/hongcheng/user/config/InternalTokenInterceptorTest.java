package com.hongcheng.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.common.constant.CommonConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内部凭证拦截器测试：/internal/** 仅认 X-Internal-Token，外部直连与伪造均 401。
 */
class InternalTokenInterceptorTest {

    private static final String TOKEN = "hc-internal-2026-dev-token";

    private InternalTokenInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new InternalTokenInterceptor(TOKEN, new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("携带正确内部凭证 - 放行")
    void preHandle_whenValidToken_passes() throws Exception {
        request.addHeader(CommonConstants.HEADER_INTERNAL_TOKEN, TOKEN);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("凭证错误 - 401 且提示内部调用凭证无效")
    void preHandle_whenWrongToken_returns401() throws Exception {
        request.addHeader(CommonConstants.HEADER_INTERNAL_TOKEN, "forged-token");

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("内部调用凭证无效");
    }

    @Test
    @DisplayName("缺失凭证 - 401（外部绕过网关直连被挡）")
    void preHandle_whenMissingToken_returns401() throws Exception {
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
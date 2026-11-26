package com.hongcheng.review.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.common.constant.CommonConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评价回复与管理拦截器 RBAC 测试：仅放行 X-User-Role=ADMIN，静态 X-Admin-Token 与普通角色一律 403。
 */
class AdminTokenInterceptorTest {

    private AdminTokenInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new AdminTokenInterceptor(new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("仅携带静态 X-Admin-Token 无 ADMIN 角色 - 403 拒绝（已废除静态旁路）")
    void preHandle_withLegacyAdminTokenOnly_returns403() throws Exception {
        request.addHeader(CommonConstants.HEADER_ADMIN_TOKEN, "hc-admin-2026-dev-token");

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("403").contains("需要管理员权限");
    }

    @Test
    @DisplayName("网关透传 X-User-Role=ADMIN - 放行")
    void preHandle_withAdminRole_passes() throws Exception {
        request.addHeader(CommonConstants.HEADER_USER_ROLE, "ADMIN");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    @DisplayName("普通用户 USER 角色 - 403 且返回统一错误体")
    void preHandle_withUserRole_returns403() throws Exception {
        request.addHeader(CommonConstants.HEADER_USER_ROLE, "USER");

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("403").contains("需要管理员权限");
    }

    @Test
    @DisplayName("伪造错误 Token 且无角色 - 403")
    void preHandle_withWrongTokenAndNoRole_returns403() throws Exception {
        request.addHeader(CommonConstants.HEADER_ADMIN_TOKEN, "forged-token");

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("无任何凭证 - 403")
    void preHandle_withoutCredentials_returns403() throws Exception {
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }
}
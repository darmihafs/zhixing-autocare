package com.hongcheng.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.common.constant.CommonConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单管理端角色拦截器测试：仅信网关透传 X-User-Role=ADMIN，其余一律 403。
 */
class AdminRoleInterceptorTest {

    private AdminRoleInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new AdminRoleInterceptor(new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("X-User-Role=ADMIN - 放行")
    void preHandle_withAdminRole_passes() throws Exception {
        request.addHeader(CommonConstants.HEADER_USER_ROLE, "ADMIN");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("普通用户 USER - 403 且返回统一错误体")
    void preHandle_withUserRole_returns403() throws Exception {
        request.addHeader(CommonConstants.HEADER_USER_ROLE, "USER");

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("403").contains("需要管理员权限");
    }

    @Test
    @DisplayName("缺失角色头 - 403")
    void preHandle_withoutRole_returns403() throws Exception {
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("仅携带旧 X-Admin-Token 无角色 - 订单管理端不放行")
    void preHandle_withOnlyAdminToken_returns403() throws Exception {
        request.addHeader(CommonConstants.HEADER_ADMIN_TOKEN, "some-token");

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }
}
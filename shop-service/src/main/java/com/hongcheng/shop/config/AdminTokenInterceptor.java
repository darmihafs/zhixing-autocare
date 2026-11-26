package com.hongcheng.shop.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 管理端权限校验：/manage/** 仅放行网关验签后透传 {@code X-User-Role == ADMIN} 的请求，其余返回 403。
 * <p>
 * 已废除静态 X-Admin-Token 旁路；伪造身份头在网关入口已被无条件剥离。
 */
@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public AdminTokenInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String role = request.getHeader(CommonConstants.HEADER_USER_ROLE);
        if (CommonConstants.ROLE_ADMIN.equals(role)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(ResultCode.FORBIDDEN.getCode(), "需要管理员权限")));
        return false;
    }
}

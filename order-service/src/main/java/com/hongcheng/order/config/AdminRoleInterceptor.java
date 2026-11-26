package com.hongcheng.order.config;

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
 * 管理员角色校验：/order/manage/** 仅放行网关透传 X-User-Role == ADMIN 的请求。
 * <p>
 * 角色头由 gateway 验签后注入并先剥离客户端伪造值，下游只信该头；
 * 普通用户（USER）或缺失角色一律 403。
 */
@Component
public class AdminRoleInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public AdminRoleInterceptor(ObjectMapper objectMapper) {
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
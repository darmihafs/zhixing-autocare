package com.hongcheng.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录态兜底拦截器：受保护路径必须携带网关注入的 X-User-Id 头。
 * <p>
 * 正常流量经 gateway 验签后注入该头；直连 8081 且无头/非法头一律 401。
 * 开发环境允许带合法头直连（便于联调），生产应通过网络隔离禁止直连。
 */
@Component
@RequiredArgsConstructor
public class AuthHeaderInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String userId = request.getHeader(CommonConstants.HEADER_USER_ID);
        if (!StringUtils.hasText(userId) || !userId.matches("\\d{1,19}")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.UNAUTHORIZED)));
            return false;
        }
        return true;
    }
}
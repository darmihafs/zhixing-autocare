package com.hongcheng.product.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 内部调用凭证拦截器：/internal/** 必须携带与配置一致的 X-Internal-Token。
 * <p>
 * 与网关 JWT 鉴权互补：网关挡外部匿名流量，本拦截器挡绕过网关的直连调用。
 */
@Component
public class InternalTokenInterceptor implements HandlerInterceptor {

    private final String internalToken;
    private final ObjectMapper objectMapper;

    public InternalTokenInterceptor(@Value("${hc.internal.token}") String internalToken,
                                    ObjectMapper objectMapper) {
        this.internalToken = internalToken;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String token = request.getHeader(CommonConstants.HEADER_INTERNAL_TOKEN);
        if (!internalToken.equals(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.fail(ResultCode.UNAUTHORIZED.getCode(), "内部调用凭证无效")));
            return false;
        }
        return true;
    }
}
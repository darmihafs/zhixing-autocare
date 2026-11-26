package com.hongcheng.shop.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * /internal/** 内部接口凭证校验（与 product-service 同一机制）。
 * 网关不路由 /internal 前缀，外部无法触达；服务间调用必须带 X-Internal-Token。
 */
@Component
@RequiredArgsConstructor
public class InternalTokenInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Value("${hc.internal.token}")
    private String expectedToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String token = request.getHeader(CommonConstants.HEADER_INTERNAL_TOKEN);
        if (expectedToken.equals(token)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(ResultCode.UNAUTHORIZED.getCode(), "内部接口凭证无效")));
        return false;
    }
}
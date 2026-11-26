package com.hongcheng.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.common.config.JwtProperties;
import com.hongcheng.common.constant.CommonConstants;
import com.hongcheng.common.result.Result;
import com.hongcheng.common.result.ResultCode;
import com.hongcheng.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * JWT 鉴权全局过滤器。
 * <p>
 * 入口先行校验路径规范性（拦截未规范化 {@code ..} 路径穿越）并无条件剥离客户端伪造的
 * X-User-Id/X-Username/X-User-Role/X-Impersonator-Id/X-Admin-Token/X-Internal-Token 头：
 * 白名单路径清头后放行；可选鉴权路径按 Token 存在性透传验签身份；其余路径强验 Bearer Token。
 * 下游服务只信网关注入的身份头，不再重复验签。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    /** 匿名可访问的路径前缀（StripPrefix 前的网关侧路径）：认证 + 公开浏览（商品/门店/评价/晒图）+ WS 握手 */
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/auth/", "/api/product/", "/api/shop/",
            "/api/review/item/", "/api/review/shop/", "/api/review/detail/",
            "/api/review/uploads/",
            "/ws/pay", "/ws/order");

    /**
     * 可选鉴权前缀：匿名即可访问（不返回 401），但携带有效登录态时正常透传 X-User-Id。
     * 无论是否登录，都先清除客户端伪造的身份头，杜绝越权。AI 导购对话属此类。
     */
    private static final List<String> OPTIONAL_AUTH_PREFIXES = List.of("/api/ai/");

    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (hasPathTraversal(exchange.getRequest())) {
            log.warn("拦截未规范化越权路径 path={}, rawPath={}",
                    exchange.getRequest().getURI().getPath(), exchange.getRequest().getURI().getRawPath());
            return writeError(exchange, HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN.getCode(), "非法请求路径");
        }

        // 所有分支（含 PUBLIC_PREFIXES）统一先剥离客户端伪造的信任/管理/内部凭证头
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(this::stripTrustHeaders)
                .build();
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        String path = sanitizedRequest.getURI().getPath();
        if (PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
            return chain.filter(sanitizedExchange);
        }
        if (OPTIONAL_AUTH_PREFIXES.stream().anyMatch(path::startsWith)) {
            return optionalAuth(sanitizedExchange, chain, path);
        }

        String authorization = sanitizedRequest.getHeaders().getFirst(CommonConstants.HEADER_AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(CommonConstants.TOKEN_PREFIX)) {
            return unauthorized(sanitizedExchange, "缺少登录凭证");
        }
        String token = authorization.substring(CommonConstants.TOKEN_PREFIX.length());
        Claims claims;
        try {
            claims = JwtUtil.parseToken(jwtProperties.getSecret(), token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Token 校验失败 path={}, reason={}", path, e.getMessage());
            return unauthorized(sanitizedExchange, "登录已过期或无效");
        }

        String userId = claims.getSubject();
        String username = claims.get(CommonConstants.CLAIM_USERNAME, String.class);
        String role = claims.get(CommonConstants.CLAIM_ROLE, String.class);
        String impersonatorId = claims.get(CommonConstants.CLAIM_IMPERSONATOR_ID, String.class);
        ServerHttpRequest request = sanitizedRequest.mutate()
                .headers(headers -> {
                    stripTrustHeaders(headers);
                    headers.set(CommonConstants.HEADER_USER_ID, userId);
                    if (username != null) {
                        headers.set(CommonConstants.HEADER_USERNAME, username);
                    }
                    headers.set(CommonConstants.HEADER_USER_ROLE, role == null ? CommonConstants.ROLE_USER : role);
                    if (impersonatorId != null) {
                        headers.set(CommonConstants.HEADER_IMPERSONATOR_ID, impersonatorId);
                    }
                })
                .build();
        return chain.filter(sanitizedExchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    /**
     * 可选鉴权：仅当携带有效 Bearer Token 时注入验签身份，否则匿名放行（绝不 401）。
     */
    private Mono<Void> optionalAuth(ServerWebExchange exchange, GatewayFilterChain chain, String path) {
        String authorization = exchange.getRequest().getHeaders().getFirst(CommonConstants.HEADER_AUTHORIZATION);
        String userId = null;
        String username = null;
        String role = null;
        String impersonatorId = null;
        if (authorization != null && authorization.startsWith(CommonConstants.TOKEN_PREFIX)) {
            String token = authorization.substring(CommonConstants.TOKEN_PREFIX.length());
            try {
                Claims claims = JwtUtil.parseToken(jwtProperties.getSecret(), token);
                userId = claims.getSubject();
                username = claims.get(CommonConstants.CLAIM_USERNAME, String.class);
                role = claims.get(CommonConstants.CLAIM_ROLE, String.class);
                impersonatorId = claims.get(CommonConstants.CLAIM_IMPERSONATOR_ID, String.class);
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("AI 导购匿名/无效 token 放行 path={}, reason={}", path, e.getMessage());
            }
        }
        final String uid = userId;
        final String uname = username;
        final String urole = role;
        final String impId = impersonatorId;
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    stripTrustHeaders(headers);
                    if (uid != null) {
                        headers.set(CommonConstants.HEADER_USER_ID, uid);
                        if (uname != null) {
                            headers.set(CommonConstants.HEADER_USERNAME, uname);
                        }
                        headers.set(CommonConstants.HEADER_USER_ROLE,
                                urole == null ? CommonConstants.ROLE_USER : urole);
                        if (impId != null) {
                            headers.set(CommonConstants.HEADER_IMPERSONATOR_ID, impId);
                        }
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    /** 无条件移除客户端可能伪造的网关信任头、静态管理头及服务间内部调用头 */
    private void stripTrustHeaders(HttpHeaders headers) {
        headers.remove(CommonConstants.HEADER_USER_ID);
        headers.remove(CommonConstants.HEADER_USERNAME);
        headers.remove(CommonConstants.HEADER_USER_ROLE);
        headers.remove(CommonConstants.HEADER_IMPERSONATOR_ID);
        headers.remove(CommonConstants.HEADER_ADMIN_TOKEN);
        headers.remove(CommonConstants.HEADER_INTERNAL_TOKEN);
    }

    /** 拦截含未规范化父目录跳转（.. / %2e / \）的请求路径，防止绕过前缀白名单 */
    private boolean hasPathTraversal(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        String rawPath = request.getURI().getRawPath();
        return containsTraversalToken(path) || containsTraversalToken(rawPath);
    }

    private boolean containsTraversalToken(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.contains("%2e") || lower.indexOf('\\') >= 0) {
            return true;
        }
        for (String segment : candidate.split("/")) {
            int semi = segment.indexOf(';');
            String clean = semi >= 0 ? segment.substring(0, semi) : segment;
            if ("..".equals(clean)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeError(exchange, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED.getCode(), message);
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(Result.fail(code, message));
        } catch (JsonProcessingException e) {
            bytes = ("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
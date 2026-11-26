package com.hongcheng.order.websocket;

import com.hongcheng.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * 订单进度 WebSocket 握手鉴权：从 ?token= 解析 JWT，把 userId 写入会话属性。
 * 浏览器 WebSocket API 无法自定义 Authorization 头，开发环境用 query 传递。
 */
@Slf4j
@Component
public class OrderHandshakeInterceptor implements HandshakeInterceptor {

    private final String jwtSecret;

    public OrderHandshakeInterceptor(@Value("${hc.jwt.secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().getFirst("token");
        Long userId = token == null ? null : JwtUtil.getUserId(jwtSecret, token);
        if (userId == null) {
            log.warn("订单进度 WebSocket 握手鉴权失败 remote={}", request.getRemoteAddress());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(OrderWebSocketHandler.ATTR_USER_ID, userId);
        log.debug("订单进度 WebSocket 握手通过 userId={}", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}

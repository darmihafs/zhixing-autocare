package com.hongcheng.order.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

/**
 * WebSocket 配置：订单进度推送端点 /ws/order?token=JWT。
 * 网关以 lb:ws://order-service 代理，握手阶段校验 token；允许源通过 hc.ws.allowed-origins 配置收紧。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderWebSocketHandler orderWebSocketHandler;
    private final OrderHandshakeInterceptor orderHandshakeInterceptor;

    @Value("${hc.ws.allowed-origins:*}")
    private String allowedOrigins = "*";

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] patterns = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (patterns.length == 0) {
            patterns = new String[]{"*"};
        }
        registry.addHandler(orderWebSocketHandler, "/ws/order")
                .addInterceptors(orderHandshakeInterceptor)
                .setAllowedOriginPatterns(patterns);
    }
}

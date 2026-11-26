package com.hongcheng.pay.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

/**
 * WebSocket 配置：支付结果推送端点 /ws/pay?token=JWT。
 * <p>
 * 网关以 lb:ws:// 代理该端点（Path=/ws/pay），握手阶段由 pay-service 校验 token；
 * 允许源通过 hc.ws.allowed-origins 配置收紧。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final PayWebSocketHandler payWebSocketHandler;
    private final PayHandshakeInterceptor payHandshakeInterceptor;

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
        registry.addHandler(payWebSocketHandler, "/ws/pay")
                .addInterceptors(payHandshakeInterceptor)
                .setAllowedOriginPatterns(patterns);
    }
}

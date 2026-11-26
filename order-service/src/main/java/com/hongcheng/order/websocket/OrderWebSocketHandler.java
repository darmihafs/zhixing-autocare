package com.hongcheng.order.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.order.dto.OrderNotifyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单进度 WebSocket：按 userId 维护在线会话，状态流转后主动推送。
 * <p>
 * 与支付 WS 相同的会话安全策略：同一用户多端登录全部推送，发送按 session 同步；
 * 前端每 15s 发一次 PING，本端回 PONG 完成心跳保活。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderWebSocketHandler extends TextWebSocketHandler {

    public static final String ATTR_USER_ID = "userId";

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
        userSessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("订单进度 WebSocket 已连接 userId={}, sessionId={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        if ("PING".equalsIgnoreCase(message.getPayload())) {
            synchronized (session) {
                session.sendMessage(new TextMessage("PONG"));
            }
            log.debug("订单进度 WebSocket 心跳 userId={}", session.getAttributes().get(ATTR_USER_ID));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }
        log.info("订单进度 WebSocket 已断开 userId={}, sessionId={}, code={}",
                userId, session.getId(), status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("订单进度 WebSocket 传输异常 sessionId={}, message={}",
                session.getId(), exception.getMessage());
    }

    /**
     * 向指定用户全部在线会话推送订单进度。
     *
     * @return 成功推送的会话数；0 表示不在线，业务结果仍以查询接口为准
     */
    public int push(Long userId, OrderNotifyMessage message) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("用户不在线，跳过订单进度推送 userId={}, orderNo={}", userId, message.getOrderNo());
            return 0;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("订单进度序列化失败 orderNo={}", message.getOrderNo(), e);
            return 0;
        }
        int sent = 0;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
                sent++;
            } catch (IOException e) {
                log.warn("订单进度推送失败 userId={}, sessionId={}", userId, session.getId());
            }
        }
        log.info("订单进度已推送 userId={}, orderNo={}, type={}, sessions={}",
                userId, message.getOrderNo(), message.getType(), sent);
        return sent;
    }
}

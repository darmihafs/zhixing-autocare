package com.hongcheng.pay.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.pay.dto.PayNotifyMessage;
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
 * 支付结果 WebSocket 推送：按 userId 维护在线会话。
 * <p>
 * 同一次支付结果只推送一次（由回调状态 CAS 成功方触发）；
 * 用户多端登录时向该用户所有会话推送。会话发送非线程安全，按 session 同步。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayWebSocketHandler extends TextWebSocketHandler {

    /** 握手拦截器写入的用户ID属性名 */
    public static final String ATTR_USER_ID = "userId";

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
        userSessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("支付结果 WebSocket 已连接 userId={}, sessionId={}, online={}",
                userId, session.getId(), count(userId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        // 仅做心跳应答，业务数据只由服务端推送
        if ("PING".equalsIgnoreCase(message.getPayload())) {
            synchronized (session) {
                session.sendMessage(new TextMessage("PONG"));
            }
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
        log.info("支付结果 WebSocket 已断开 userId={}, sessionId={}, code={}",
                userId, session.getId(), status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("支付结果 WebSocket 传输异常 sessionId={}, message={}",
                session.getId(), exception.getMessage());
    }

    /**
     * 向指定用户全部在线会话推送支付结果。
     *
     * @return 实际推送成功的会话数（0 表示用户当前不在线，业务结果仍以查询接口为准）
     */
    public int push(Long userId, PayNotifyMessage message) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.info("用户不在线，跳过 WebSocket 推送 userId={}, outTradeNo={}",
                    userId, message.getOutTradeNo());
            return 0;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("支付结果序列化失败 outTradeNo={}", message.getOutTradeNo(), e);
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
                log.warn("支付结果推送失败 userId={}, sessionId={}", userId, session.getId());
            }
        }
        log.info("支付结果已推送 userId={}, outTradeNo={}, type={}, sessions={}",
                userId, message.getOutTradeNo(), message.getType(), sent);
        return sent;
    }

    private int count(Long userId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions == null ? 0 : sessions.size();
    }
}

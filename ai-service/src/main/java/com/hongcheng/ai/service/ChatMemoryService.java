package com.hongcheng.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hongcheng.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 多轮会话记忆：Redis DB7，按 userId + sessionId 隔离缓存最近 N 轮（user+assistant）上下文。
 * 已登录键为 {@code ai:chat:u:{userId}:{sessionId}}，匿名键为 {@code ai:chat:anon:{sessionId}}，
 * 防止不同用户传入同一 sessionId 串读对话；只存最终问答文本，每次写入刷新 TTL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private static final String KEY_USER_PREFIX = "ai:chat:u:";
    private static final String KEY_ANON_PREFIX = "ai:chat:anon:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final AiProperties props;

    public List<ObjectNode> load(Long userId, String sessionId) {
        List<ObjectNode> out = new ArrayList<>();
        if (sessionId == null || sessionId.isBlank()) return out;
        try {
            String json = redis.opsForValue().get(buildKey(userId, sessionId));
            if (json == null || json.isBlank()) return out;
            ArrayNode arr = (ArrayNode) mapper.readTree(json);
            arr.forEach(n -> {
                if (n.isObject()) out.add((ObjectNode) n);
            });
        } catch (Exception e) {
            log.warn("读取会话记忆失败 userId={}, sessionId={}, reason={}", userId, sessionId, e.getMessage());
        }
        return out;
    }

    public void append(Long userId, String sessionId, String role, String content) {
        if (sessionId == null || sessionId.isBlank() || content == null || content.isBlank()) return;
        try {
            List<ObjectNode> history = load(userId, sessionId);
            ObjectNode msg = mapper.createObjectNode();
            msg.put("role", role);
            msg.put("content", content);
            history.add(msg);
            int max = Math.max(1, props.getMemoryRounds()) * 2;
            while (history.size() > max) history.remove(0);
            ArrayNode arr = mapper.createArrayNode();
            history.forEach(arr::add);
            redis.opsForValue().set(buildKey(userId, sessionId), mapper.writeValueAsString(arr),
                    Duration.ofSeconds(props.getMemoryTtlSeconds()));
        } catch (Exception e) {
            log.warn("写入会话记忆失败 userId={}, sessionId={}, reason={}", userId, sessionId, e.getMessage());
        }
    }

    String buildKey(Long userId, String sessionId) {
        return userId != null ? KEY_USER_PREFIX + userId + ":" + sessionId : KEY_ANON_PREFIX + sessionId;
    }
}
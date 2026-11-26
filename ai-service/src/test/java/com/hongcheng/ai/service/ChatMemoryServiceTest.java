package com.hongcheng.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongcheng.ai.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 会话记忆用户隔离单元测试。
 */
class ChatMemoryServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ChatMemoryService chatMemoryService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        chatMemoryService = new ChatMemoryService(redisTemplate, new ObjectMapper(), new AiProperties());
    }

    @Test
    @DisplayName("已登录用户与匿名用户使用相同 sessionId 时 Redis Key 严格隔离")
    void buildKey_isolatesAuthenticatedAndAnonymousUsers() {
        assertThat(chatMemoryService.buildKey(101L, "s1")).isEqualTo("ai:chat:u:101:s1");
        assertThat(chatMemoryService.buildKey(202L, "s1")).isEqualTo("ai:chat:u:202:s1");
        assertThat(chatMemoryService.buildKey(null, "s1")).isEqualTo("ai:chat:anon:s1");
    }

    @Test
    @DisplayName("追加对话写入用户隔离键并刷新 TTL")
    void append_writesToScopedRedisKey() {
        when(valueOps.get("ai:chat:u:7:sess1")).thenReturn(null);

        chatMemoryService.append(7L, "sess1", "user", "换机油多少钱");

        verify(valueOps).set(eq("ai:chat:u:7:sess1"), anyString(), eq(Duration.ofSeconds(7200)));
    }
}

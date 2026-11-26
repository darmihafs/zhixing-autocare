package com.hongcheng.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hongcheng.ai.config.AiProperties;
import com.hongcheng.ai.dto.ChatRequest;
import com.hongcheng.ai.dto.ToolCall;
import com.hongcheng.ai.dto.ToolResult;
import com.hongcheng.ai.llm.Completion;
import com.hongcheng.ai.llm.LlmClient;
import com.hongcheng.ai.llm.LlmUnavailableException;
import com.hongcheng.ai.llm.StreamListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 智能体降级与 ReAct 单测：未配 Key 直接降级（大模型零调用）、大模型超时降级、工具调用后二次生成。
 */
@ExtendWith(MockitoExtension.class)
class AgentFallbackTest {

    @Mock
    private LlmClient llm;
    @Mock
    private ChatMemoryService memory;
    @Mock
    private ToolExecutor toolExecutor;
    @Mock
    private RecommendFallbackService fallbackService;

    private final ObjectMapper mapper = new ObjectMapper();
    private AiProperties props;
    private AgentService agent;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        agent = new AgentService(props, memory, toolExecutor, fallbackService, llm, mapper);
    }

    private ChatRequest req(String message) {
        ChatRequest r = new ChatRequest();
        r.setSessionId("s1");
        r.setMessage(message);
        return r;
    }

    private SseEmitter emitter() {
        return new SseEmitter(60_000L);
    }

    @Test
    @DisplayName("未配 Key - 直接本地检索降级，大模型零调用，记忆按 userId 隔离写入")
    void run_whenNoKey_degradesWithoutTouchingLlm() {
        props.setApiKey("");
        when(fallbackService.build("附近哪里可以洗车？", null))
                .thenReturn(new ToolResult("本地结果", mapper.createObjectNode()));

        agent.run(req("附近哪里可以洗车？"), null, emitter());

        verifyNoInteractions(llm);
        verify(fallbackService).build("附近哪里可以洗车？", null);
        verify(memory).append(null, "s1", "user", "附近哪里可以洗车？");
        verify(memory).append(null, "s1", "assistant", "本地结果");
    }

    @Test
    @DisplayName("大模型超时 - 捕获异常后降级本地检索，接口不抛错")
    void run_whenLlmTimesOut_degrades() {
        props.setApiKey("test-key");
        when(memory.load(null, "s1")).thenReturn(List.of());
        when(llm.stream(any(), any(), any())).thenThrow(new LlmUnavailableException("timeout"));
        when(fallbackService.build("机油", null)).thenReturn(new ToolResult("本地结果", mapper.createObjectNode()));

        agent.run(req("机油"), null, emitter());

        verify(llm).stream(any(), any(), any());
        verify(fallbackService).build("机油", null);
        verify(memory).append(null, "s1", "assistant", "本地结果");
    }

    @Test
    @DisplayName("ReAct - 首轮工具调用后二次生成文本，dispatch 命中且记忆按 userId 写入")
    void run_reactLoop_dispatchesToolThenGenerates() {
        props.setApiKey("test-key");
        props.setMaxToolRounds(3);
        when(memory.load(7L, "s1")).thenReturn(List.of());
        ObjectNode cards = mapper.createObjectNode();
        cards.putArray("products");
        when(toolExecutor.emptyCards()).thenReturn(cards);
        ToolCall tc = new ToolCall("call_1", "search_products", "{\"keyword\":\"机油\"}");
        when(toolExecutor.dispatch(tc, 7L)).thenReturn(new ToolResult("找到 1 件", cards));
        when(llm.stream(any(), any(), any()))
                .thenReturn(new Completion("tool_calls", "", List.of(tc)))
                .thenAnswer(inv -> {
                    StreamListener listener = inv.getArgument(2);
                    listener.onDelta("推荐全合成机油");
                    return new Completion("stop", "推荐全合成机油", null);
                });

        agent.run(req("机油推荐"), 7L, emitter());

        verify(memory).load(7L, "s1");
        verify(toolExecutor).dispatch(tc, 7L);
        verify(llm, times(2)).stream(any(), any(), any());
        verify(memory).append(7L, "s1", "assistant", "推荐全合成机油");
    }
}
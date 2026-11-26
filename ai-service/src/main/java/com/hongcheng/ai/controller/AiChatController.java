package com.hongcheng.ai.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.hongcheng.ai.dto.ChatRequest;
import com.hongcheng.ai.service.AgentService;
import com.hongcheng.common.constant.CommonConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * 养车顾问对话入口。SSE 流式返回 meta/status/delta/cards/done/error 事件。
 * <p>
 * X-User-Id 由网关在携带有效登录态时注入；匿名请求该头缺失（userId=null），
 * query_my_orders 工具据此拦截，绝不越权。Sentinel 限流/熔断/异常一律同步降级，永不 500。
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiChatController {

    private static final long SSE_TIMEOUT_MS = 60_000L;

    private final AgentService agentService;
    private final ExecutorService aiAgentExecutor;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SentinelResource(value = "aiChat", blockHandler = "chatBlock", fallback = "chatFallback")
    public SseEmitter chat(@Valid @RequestBody ChatRequest req,
                           @RequestHeader(value = CommonConstants.HEADER_USER_ID, required = false) Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        try {
            aiAgentExecutor.execute(() -> agentService.run(req, userId, emitter));
        } catch (RejectedExecutionException e) {
            log.warn("AI 线程池已满，同步降级 userId={}", userId);
            agentService.runDegraded(req, userId, emitter, "当前咨询较多，已为你切换本地真实检索。");
        }
        return emitter;
    }

    /** Sentinel 限流/熔断：同步本地检索降级 */
    public SseEmitter chatBlock(ChatRequest req, Long userId, BlockException ex) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        agentService.runDegraded(req, userId, emitter, "咨询人数较多，已为你切换本地真实检索。");
        return emitter;
    }

    /** 业务异常兜底：同步本地检索降级 */
    public SseEmitter chatFallback(ChatRequest req, Long userId, Throwable ex) {
        log.warn("AI 对话触发 Sentinel fallback userId={}, reason={}", userId, ex.toString());
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        agentService.runDegraded(req, userId, emitter, "服务繁忙，已为你切换本地真实检索。");
        return emitter;
    }
}
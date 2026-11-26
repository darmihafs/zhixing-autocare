package com.hongcheng.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hongcheng.ai.config.AiProperties;
import com.hongcheng.ai.dto.ChatRequest;
import com.hongcheng.ai.dto.ToolCall;
import com.hongcheng.ai.dto.ToolResult;
import com.hongcheng.ai.llm.Completion;
import com.hongcheng.ai.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * ReAct 导购智能体：系统提示 + Redis 多轮记忆 + 用户消息，最多 3 轮工具调用；
 * 文本增量实时 SSE 下发，工具进度以 status 事件提示，结构化卡片以 cards 事件下发。
 * 未配 Key / 大模型异常时整体降级为本地真实检索，保证永不 500。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final String SYSTEM_PROMPT = """
            你是"洪兴洗车"平台的智能养车顾问，帮助用户选购汽车养护商品、查找附近门店与养车服务、查询自己的订单。
            规则：
            1. 需要真实商品/门店/服务/订单数据时，必须调用工具，严禁编造名称、价格、库存或订单状态。
            2. search_products 查在售商品；search_shops_and_services 查门店与服务项目；query_my_orders 查当前登录用户订单。
            3. 用户未登录却想查订单时，明确告知需先登录，不要臆造订单。
            4. 回答简洁、口语化、中文，结合工具返回的真实数据给出建议；推荐卡片由系统单独渲染，你只需给出文字说明。
            5. 最多进行 3 轮工具调用，拿到足够信息后直接给出结论。
            """;

    private final AiProperties props;
    private final ChatMemoryService memory;
    private final ToolExecutor toolExecutor;
    private final RecommendFallbackService fallbackService;
    private final LlmClient llm;
    private final ObjectMapper mapper;

    /** 正常入口：有 Key 走 ReAct，无 Key 或异常走降级；无论如何都 complete，不抛异常。 */
    public void run(ChatRequest req, Long userId, SseEmitter emitter) {
        String sessionId = resolveSessionId(req);
        try {
            send(emitter, "meta", meta(sessionId));
            if (!props.hasKey()) {
                fallbackFlow(req, userId, emitter, sessionId, "未配置大模型 API Key，已为你切换本地真实检索。");
            } else {
                reactFlow(req, userId, emitter, sessionId);
            }
        } catch (Exception e) {
            log.warn("AI 对话异常，降级本地检索 sessionId={}, reason={}", sessionId, e.toString());
            try {
                fallbackFlow(req, userId, emitter, sessionId, "大模型暂不可用，已为你切换本地真实检索。");
            } catch (Exception ignore) {
                send(emitter, "error", text("服务繁忙，请稍后重试。"));
            }
        } finally {
            safeComplete(emitter);
        }
    }

    /** 线程池拒绝 / Sentinel 熔断限流时的同步降级入口。 */
    public void runDegraded(ChatRequest req, Long userId, SseEmitter emitter, String reason) {
        String sessionId = resolveSessionId(req);
        try {
            send(emitter, "meta", meta(sessionId));
            fallbackFlow(req, userId, emitter, sessionId, reason);
        } catch (Exception e) {
            log.warn("降级流程异常 sessionId={}, reason={}", sessionId, e.toString());
            send(emitter, "error", text("服务繁忙，请稍后重试。"));
        } finally {
            safeComplete(emitter);
        }
    }

    private void reactFlow(ChatRequest req, Long userId, SseEmitter emitter, String sessionId) {
        ArrayNode messages = mapper.createArrayNode();
        messages.add(message("system", SYSTEM_PROMPT));
        List<ObjectNode> history = memory.load(userId, sessionId);
        for (ObjectNode h : history) {
            messages.add(message(h.path("role").asText("user"), h.path("content").asText("")));
        }
        messages.add(message("user", req.getMessage()));

        ArrayNode tools = toolSchemas();
        ObjectNode cards = toolExecutor.emptyCards();
        StringBuilder finalText = new StringBuilder();
        int maxRounds = Math.max(1, props.getMaxToolRounds());

        for (int round = 0; round <= maxRounds; round++) {
            Completion c = llm.stream(messages, tools, delta -> {
                finalText.append(delta);
                send(emitter, "delta", text(delta));
            });
            if (c.hasToolCalls() && round < maxRounds) {
                messages.add(assistantToolCalls(c));
                for (ToolCall tc : c.getToolCalls()) {
                    send(emitter, "status", text(statusText(tc)));
                    ToolResult tr = toolExecutor.dispatch(tc, userId);
                    toolExecutor.mergeCards(cards, tr.getCards());
                    messages.add(toolMessage(tc.getId(), tr.getText()));
                }
                continue;
            }
            break;
        }

        if (finalText.length() == 0) {
            String closing = "以上是为你检索到的真实商品与门店服务，点击卡片即可查看详情。";
            finalText.append(closing);
            send(emitter, "delta", text(closing));
        }
        send(emitter, "cards", cards);
        memory.append(userId, sessionId, "user", req.getMessage());
        memory.append(userId, sessionId, "assistant", finalText.toString());
        send(emitter, "done", done(sessionId, false));
    }

    private void fallbackFlow(ChatRequest req, Long userId, SseEmitter emitter, String sessionId, String reason) {
        send(emitter, "status", text(reason));
        ToolResult fr = fallbackService.build(req.getMessage(), userId);
        send(emitter, "delta", text(fr.getText()));
        send(emitter, "cards", fr.getCards());
        memory.append(userId, sessionId, "user", req.getMessage());
        memory.append(userId, sessionId, "assistant", fr.getText());
        send(emitter, "done", done(sessionId, true));
    }

    // ---------- 消息构造 ----------

    private ObjectNode message(String role, String content) {
        ObjectNode m = mapper.createObjectNode();
        m.put("role", role);
        m.put("content", content == null ? "" : content);
        return m;
    }

    private ObjectNode assistantToolCalls(Completion c) {
        ObjectNode m = mapper.createObjectNode();
        m.put("role", "assistant");
        m.put("content", c.getContent() == null ? "" : c.getContent());
        ArrayNode arr = m.putArray("tool_calls");
        for (ToolCall tc : c.getToolCalls()) {
            ObjectNode o = arr.addObject();
            o.put("id", tc.getId());
            o.put("type", "function");
            ObjectNode fn = o.putObject("function");
            fn.put("name", tc.getName());
            fn.put("arguments", tc.getArguments() == null ? "{}" : tc.getArguments());
        }
        return m;
    }

    private ObjectNode toolMessage(String id, String content) {
        ObjectNode m = mapper.createObjectNode();
        m.put("role", "tool");
        m.put("tool_call_id", id == null ? "" : id);
        m.put("content", content == null ? "" : content);
        return m;
    }

    private ArrayNode toolSchemas() {
        ArrayNode tools = mapper.createArrayNode();
        tools.add(functionTool(ToolExecutor.TOOL_SEARCH_PRODUCTS,
                "按关键词检索平台真实在售商品，返回名称、价格、库存、图片与ID",
                keywordParams()));
        tools.add(functionTool(ToolExecutor.TOOL_SEARCH_SHOPS,
                "按关键词检索真实门店与养车服务项目（ES 分词），返回门店与服务的名称、价格、图片与ID",
                keywordParams()));
        tools.add(functionTool(ToolExecutor.TOOL_QUERY_ORDERS,
                "查询当前登录用户最近的订单状态；用户未登录时不可用，需提示先登录",
                mapper.createObjectNode().put("type", "object").set("properties", mapper.createObjectNode())));
        return tools;
    }

    private ObjectNode keywordParams() {
        ObjectNode params = mapper.createObjectNode();
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");
        ObjectNode kw = props.putObject("keyword");
        kw.put("type", "string");
        kw.put("description", "检索关键词，如 机油、洗车、轮胎、空调保养");
        params.putArray("required").add("keyword");
        return params;
    }

    private ObjectNode functionTool(String name, String description, ObjectNode parameters) {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode fn = tool.putObject("function");
        fn.put("name", name);
        fn.put("description", description);
        fn.set("parameters", parameters);
        return tool;
    }

    private String statusText(ToolCall tc) {
        String name = tc.getName() == null ? "" : tc.getName();
        return switch (name) {
            case ToolExecutor.TOOL_SEARCH_PRODUCTS -> "正在检索商品…";
            case ToolExecutor.TOOL_SEARCH_SHOPS -> "正在检索门店与服务…";
            case ToolExecutor.TOOL_QUERY_ORDERS -> "正在查询你的订单…";
            default -> "正在思考…";
        };
    }

    // ---------- SSE 事件 ----------

    private String resolveSessionId(ChatRequest req) {
        String sid = req.getSessionId();
        return (sid == null || sid.isBlank()) ? UUID.randomUUID().toString().replace("-", "") : sid;
    }

    private ObjectNode meta(String sessionId) {
        ObjectNode n = mapper.createObjectNode();
        n.put("sessionId", sessionId);
        return n;
    }

    private ObjectNode done(String sessionId, boolean degraded) {
        ObjectNode n = mapper.createObjectNode();
        n.put("sessionId", sessionId);
        n.put("degraded", degraded);
        return n;
    }

    private ObjectNode text(String t) {
        ObjectNode n = mapper.createObjectNode();
        n.put("text", t == null ? "" : t);
        return n;
    }

    private void send(SseEmitter emitter, String name, ObjectNode payload) {
        try {
            emitter.send(SseEmitter.event().name(name).data(payload.toString(), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // 客户端断开或 emitter 已完成，忽略即可
            log.debug("SSE 发送中断 event={}, reason={}", name, e.getMessage());
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignore) {
            // 已完成/已断开
        }
    }
}
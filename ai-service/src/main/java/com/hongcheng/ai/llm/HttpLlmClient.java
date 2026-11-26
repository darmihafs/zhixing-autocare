package com.hongcheng.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hongcheng.ai.config.AiProperties;
import com.hongcheng.ai.dto.ToolCall;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 基于 JDK HttpClient 的 OpenAI 兼容流式客户端（不引额外 SDK）。
 * 解析上游 SSE：content 增量实时回调；tool_calls 按 index 聚合 arguments 片段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpLlmClient implements LlmClient {

    private final AiProperties props;
    private final ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(3000))
            .build();

    @Override
    public Completion stream(ArrayNode messages, ArrayNode tools, StreamListener listener) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.getModel());
        body.set("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }
        body.put("stream", true);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(props.getBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofMillis(props.getRequestTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            throw new LlmUnavailableException("构造大模型请求失败: " + e.getMessage(), e);
        }

        log.info("LLM request model={} url={}/chat/completions tools={}",
                props.getModel(), props.getBaseUrl(), tools == null ? 0 : tools.size());
        try {
            HttpResponse<InputStream> resp = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream bodyStream = resp.body()) {
                if (resp.statusCode() != 200) {
                    String err = bodyStream == null ? "" : new String(bodyStream.readNBytes(300), StandardCharsets.UTF_8);
                    throw new LlmUnavailableException("大模型返回 HTTP " + resp.statusCode() + ": " + err);
                }
                Completion completion = parseSse(bodyStream, listener);
                log.info("LLM response model={} finish={} toolCalls={} contentLen={}",
                        props.getModel(), completion.getFinishReason(),
                        completion.getToolCalls() == null ? 0 : completion.getToolCalls().size(),
                        completion.getContent().length());
                return completion;
            }
        } catch (IOException e) {
            throw new LlmUnavailableException("大模型网络/超时异常: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException("大模型调用被中断", e);
        }
    }

    /**
     * 解析上游 SSE 流（独立静态方法便于单测直接喂字节流）。
     */
    public Completion parseSse(InputStream in, StreamListener listener) throws IOException {
        StringBuilder content = new StringBuilder();
        Map<Integer, ToolCall> acc = new TreeMap<>();
        String finish = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String payload = line.substring(5).trim();
                if (payload.isEmpty()) continue;
                if ("[DONE]".equals(payload)) break;
                JsonNode chunk = mapper.readTree(payload);
                JsonNode choice = chunk.path("choices").path(0);
                JsonNode delta = choice.path("delta");
                JsonNode c = delta.path("content");
                if (c.isTextual() && !c.asText().isEmpty()) {
                    content.append(c.asText());
                    if (listener != null) listener.onDelta(c.asText());
                }
                JsonNode tcs = delta.path("tool_calls");
                if (tcs.isArray()) {
                    for (JsonNode tc : tcs) {
                        int idx = tc.path("index").asInt(0);
                        ToolCall call = acc.computeIfAbsent(idx, k -> new ToolCall());
                        if (tc.hasNonNull("id")) call.setId(tc.get("id").asText());
                        JsonNode fn = tc.path("function");
                        if (fn.hasNonNull("name")) {
                            call.setName((call.getName() == null ? "" : call.getName()) + fn.get("name").asText());
                        }
                        if (fn.hasNonNull("arguments")) {
                            call.setArguments((call.getArguments() == null ? "" : call.getArguments()) + fn.get("arguments").asText());
                        }
                    }
                }
                JsonNode fr = choice.path("finish_reason");
                if (fr.isTextual()) finish = fr.asText();
            }
        }
        List<ToolCall> calls = new ArrayList<>(acc.values());
        return new Completion(finish == null ? "stop" : finish, content.toString(), calls);
    }
}
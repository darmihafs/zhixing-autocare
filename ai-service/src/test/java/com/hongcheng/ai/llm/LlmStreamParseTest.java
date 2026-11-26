package com.hongcheng.ai.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hongcheng.ai.config.AiProperties;
import com.hongcheng.ai.dto.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上游 SSE 解析单测：文本增量实时回调 + 聚合；tool_calls 按 index 拼接 arguments 片段；finish_reason。
 */
class LlmStreamParseTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpLlmClient client = new HttpLlmClient(new AiProperties(), mapper);

    private String frame(ObjectNode chunk) throws Exception {
        return "data: " + mapper.writeValueAsString(chunk) + "\n\n";
    }

    private ObjectNode textChunk(String content, String finish) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode choice = root.putArray("choices").addObject();
        ObjectNode delta = choice.putObject("delta");
        if (content != null) delta.put("content", content);
        if (finish != null) choice.put("finish_reason", finish);
        else choice.putNull("finish_reason");
        return root;
    }

    private ObjectNode toolChunk(int index, String id, String name, String argsFragment) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode choice = root.putArray("choices").addObject();
        choice.putNull("finish_reason");
        ObjectNode tc = choice.putObject("delta").putArray("tool_calls").addObject();
        tc.put("index", index);
        if (id != null) tc.put("id", id);
        ObjectNode fn = tc.putObject("function");
        if (name != null) fn.put("name", name);
        if (argsFragment != null) fn.put("arguments", argsFragment);
        return root;
    }

    private Completion parse(String sse, StreamListener listener) throws Exception {
        return client.parseSse(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)), listener);
    }

    @Test
    @DisplayName("纯文本流 - 增量实时回调且聚合完整，finish=stop，无工具调用")
    void parseSse_textStream_accumulatesAndCallsListener() throws Exception {
        StringBuilder live = new StringBuilder();
        String sse = frame(textChunk("你好", null))
                + frame(textChunk("，顾问", null))
                + frame(textChunk(null, "stop"))
                + "data: [DONE]\n\n";
        Completion c = parse(sse, live::append);
        assertThat(c.getContent()).isEqualTo("你好，顾问");
        assertThat(live.toString()).isEqualTo("你好，顾问");
        assertThat(c.getFinishReason()).isEqualTo("stop");
        assertThat(c.hasToolCalls()).isFalse();
    }

    @Test
    @DisplayName("工具调用流 - 按 index 聚合 arguments 片段为合法 JSON，finish=tool_calls")
    void parseSse_toolCalls_aggregatesArgumentFragments() throws Exception {
        String sse = frame(toolChunk(0, "call_1", "search_products", "{\"key"))
                + frame(toolChunk(0, null, null, "word\":\"机油\"}"))
                + frame(textChunk(null, "tool_calls"))
                + "data: [DONE]\n\n";
        Completion c = parse(sse, null);
        assertThat(c.getFinishReason()).isEqualTo("tool_calls");
        assertThat(c.hasToolCalls()).isTrue();
        assertThat(c.getToolCalls()).hasSize(1);
        ToolCall call = c.getToolCalls().get(0);
        assertThat(call.getId()).isEqualTo("call_1");
        assertThat(call.getName()).isEqualTo("search_products");
        assertThat(call.getArguments()).isEqualTo("{\"keyword\":\"机油\"}");
        assertThat(mapper.readTree(call.getArguments()).path("keyword").asText()).isEqualTo("机油");
    }

    @Test
    @DisplayName("空流/仅 DONE - 返回 stop 与空内容，不抛异常")
    void parseSse_emptyStream_returnsStop() throws Exception {
        Completion c = parse("data: [DONE]\n\n", null);
        assertThat(c.getFinishReason()).isEqualTo("stop");
        assertThat(c.getContent()).isEmpty();
        assertThat(c.hasToolCalls()).isFalse();
    }
}
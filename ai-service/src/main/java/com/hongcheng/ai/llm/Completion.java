package com.hongcheng.ai.llm;

import com.hongcheng.ai.dto.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一轮 /chat/completions 流式响应的聚合结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Completion {

    /** stop / tool_calls 等 */
    private String finishReason;
    /** 本轮累积的文本内容 */
    private String content;
    /** 本轮请求的工具调用（finish_reason=tool_calls 时非空） */
    private List<ToolCall> toolCalls;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
package com.hongcheng.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 大模型返回的一次工具调用请求（OpenAI tool_calls 协议）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    private String id;
    private String name;
    /** 原始 arguments JSON 字符串 */
    private String arguments;
}
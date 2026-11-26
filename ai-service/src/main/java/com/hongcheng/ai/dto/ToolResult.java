package com.hongcheng.ai.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果：给大模型/用户看的文本摘要 + 结构化推荐卡片。
 * cards 形如 {products:[...], shops:[...], services:[...], orders:[...]}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    private String text;
    private ObjectNode cards;
}
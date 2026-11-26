package com.hongcheng.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 导购对话请求。sessionId 缺省时由服务端生成并在 meta 事件回传。
 */
@Data
public class ChatRequest {

    @Size(max = 64)
    private String sessionId;

    @NotBlank(message = "消息不能为空")
    @Size(max = 500, message = "消息过长")
    private String message;
}
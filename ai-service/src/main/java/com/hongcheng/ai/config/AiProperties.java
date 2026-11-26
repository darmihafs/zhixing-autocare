package com.hongcheng.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 顾问配置。全部敏感项仅由环境变量注入（见 application.yml），
 * 代码与配置文件不出现任何真实密钥。
 */
@Data
@ConfigurationProperties(prefix = "hc.ai")
public class AiProperties {

    /** OpenAI 兼容服务地址，默认 DeepSeek */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** 模型名 */
    private String model = "deepseek-chat";

    /** API Key；空表示未配置，触发本地检索降级 */
    private String apiKey = "";

    /** ReAct 最大工具调用轮数 */
    private int maxToolRounds = 3;

    /** 单次大模型请求超时（毫秒） */
    private long requestTimeoutMs = 20000;

    /** 会话记忆保留轮数（user+assistant 为一轮） */
    private int memoryRounds = 10;

    /** 会话记忆 TTL（秒） */
    private long memoryTtlSeconds = 7200;

    /** 是否已配置可用 Key */
    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
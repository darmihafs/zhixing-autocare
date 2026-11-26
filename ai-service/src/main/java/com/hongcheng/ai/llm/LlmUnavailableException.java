package com.hongcheng.ai.llm;

/**
 * 大模型不可用（未配 Key 由上层判断；此处覆盖超时/网络/非 200/余额不足等）。
 * 捕获后统一走本地真实检索降级，保证接口不报 500。
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
package com.hongcheng.ai.llm;

import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * OpenAI 兼容 /chat/completions 流式客户端抽象（便于单测 mock）。
 */
public interface LlmClient {

    /**
     * 发起一次流式对话；文本增量经 listener 实时回调，工具调用在结束时聚合返回。
     *
     * @throws LlmUnavailableException 超时/网络/非 200/响应异常
     */
    Completion stream(ArrayNode messages, ArrayNode tools, StreamListener listener);
}
package com.hongcheng.ai.llm;

/**
 * 流式文本增量回调。
 */
@FunctionalInterface
public interface StreamListener {

    void onDelta(String text);
}
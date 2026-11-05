package com.example.fished.agent.llm;

import java.util.function.Consumer;

/**
 * 模型客户端抽象。当前实现为 DeepSeek（OpenAI 兼容协议），
 * 后续可新增 Anthropic 实现而不改 Agent 循环。
 */
public interface LlmClient {

    /**
     * 发起一次对话。文字增量通过 onText 实时回调；返回完整结果（含工具调用）。
     */
    LlmModels.LlmResult stream(LlmModels.ChatRequest request, Consumer<String> onText);
}

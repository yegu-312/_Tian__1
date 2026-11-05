package com.example.fished.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * 与具体模型协议无关的轻量消息模型。
 */
public final class LlmModels {

    private LlmModels() {
    }

    /** role = system / user / assistant / tool */
    public record ChatMessage(String role, String content, String toolCallId, List<ToolCall> toolCalls) {

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content, null, List.of());
        }

        public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
            return new ChatMessage("assistant",
                    content == null ? "" : content, null,
                    toolCalls == null ? List.of() : List.copyOf(toolCalls));
        }

        public static ChatMessage tool(String toolCallId, String content) {
            return new ChatMessage("tool", content, toolCallId, List.of());
        }
    }

    public record ToolCall(String id, String name, String arguments) {
    }

    public record ToolSpec(String name, String description, Map<String, Object> parameters) {
    }

    public record ChatRequest(String systemPrompt, List<ChatMessage> messages, List<ToolSpec> tools) {
    }

    /** finishReason: stop / tool_calls / length / ... */
    public record LlmResult(String content, List<ToolCall> toolCalls, String finishReason) {
    }
}

package com.example.fished.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 解析 OpenAI 兼容的流式增量（DeepSeek 使用同一格式）。
 * 纯逻辑、无网络调用，便于单元测试。
 */
public final class DeepSeekStreamParser {

    private final ObjectMapper mapper;
    private final StringBuilder content = new StringBuilder();
    private final Map<Integer, ToolCallAcc> toolCalls = new TreeMap<>();
    private String finishReason;

    public DeepSeekStreamParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 处理一条 data JSON（不含 "data:" 前缀）。
     *
     * @return 本次新增的正文片段，可能为空
     */
    public String accept(String dataJson) {
        if (dataJson == null || dataJson.isBlank()) {
            return "";
        }
        try {
            JsonNode root = mapper.readTree(dataJson);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                return "";
            }
            JsonNode choice = choices.get(0);
            JsonNode delta = choice.path("delta");

            String text = delta.path("content").asText("");
            if (!text.isEmpty()) {
                content.append(text);
            }

            JsonNode toolDeltas = delta.path("tool_calls");
            if (toolDeltas.isArray()) {
                for (JsonNode toolDelta : toolDeltas) {
                    int index = toolDelta.has("index") ? toolDelta.get("index").asInt() : 0;
                    ToolCallAcc acc = toolCalls.computeIfAbsent(index, key -> new ToolCallAcc());
                    if (toolDelta.hasNonNull("id")) {
                        acc.id = toolDelta.get("id").asText();
                    }
                    JsonNode function = toolDelta.path("function");
                    if (function.hasNonNull("name")) {
                        acc.name = function.get("name").asText();
                    }
                    if (function.hasNonNull("arguments")) {
                        acc.arguments.append(function.get("arguments").asText());
                    }
                }
            }

            if (choice.hasNonNull("finish_reason")) {
                finishReason = choice.get("finish_reason").asText();
            }
            return text;
        } catch (Exception e) {
            throw new AgentException("解析模型流式响应失败", e);
        }
    }

    public boolean isDone(String data) {
        return data != null && "[DONE]".equals(data.trim());
    }

    public LlmModels.LlmResult result() {
        List<LlmModels.ToolCall> calls = new ArrayList<>();
        for (ToolCallAcc acc : toolCalls.values()) {
            if (acc.name == null || acc.name.isBlank()) {
                continue;
            }
            String id = (acc.id == null || acc.id.isBlank()) ? "call_" + (calls.size() + 1) : acc.id;
            String arguments = acc.arguments.length() == 0 ? "{}" : acc.arguments.toString();
            calls.add(new LlmModels.ToolCall(id, acc.name, arguments));
        }
        return new LlmModels.LlmResult(
                content.toString(),
                List.copyOf(calls),
                finishReason == null ? "stop" : finishReason);
    }

    private static final class ToolCallAcc {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }
}

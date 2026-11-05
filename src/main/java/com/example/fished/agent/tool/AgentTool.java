package com.example.fished.agent.tool;

import com.example.fished.agent.llm.LlmModels;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Agent 可调用的工具。写工具由 AgentService 先转为待确认操作，确认后再 execute。
 */
public interface AgentTool {

    LlmModels.ToolSpec spec();

    boolean readOnly();

    String execute(JsonNode args, ToolContext context) throws Exception;

    /** 写操作确认卡片上的中文描述。 */
    default String describe(JsonNode args) {
        return spec().description();
    }
}

package com.example.fished.agent.tool;

import com.example.fished.agent.llm.LlmModels;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具白名单：只允许注册在册的工具被执行。
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final ObjectMapper mapper;

    public ToolRegistry(ReadTools readTools, WriteTools writeTools, ObjectMapper mapper) {
        this.mapper = mapper;
        for (AgentTool tool : readTools.all()) {
            register(tool);
        }
        for (AgentTool tool : writeTools.all()) {
            register(tool);
        }
    }

    private void register(AgentTool tool) {
        String name = tool.spec().name();
        if (tools.putIfAbsent(name, tool) != null) {
            throw new IllegalStateException("工具名称重复：" + name);
        }
    }

    public List<LlmModels.ToolSpec> specs() {
        return tools.values().stream().map(AgentTool::spec).toList();
    }

    public AgentTool get(String name) {
        return tools.get(name);
    }

    public boolean isWrite(String name) {
        AgentTool tool = tools.get(name);
        return tool != null && !tool.readOnly();
    }

    /**
     * 执行工具。任何异常都转成模型可读的错误 JSON，不中断对话。
     */
    public String execute(String name, JsonNode args, ToolContext context) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return error("未知工具：" + name);
        }
        try {
            String result = tool.execute(args == null ? mapper.createObjectNode() : args, context);
            log.info("Agent 工具执行完成：{} pondId={} operator={}", name,
                    context == null ? null : context.activePondId(),
                    context == null ? null : context.operator());
            return result;
        } catch (Exception e) {
            log.warn("Agent 工具执行失败：{} - {}", name, e.getMessage());
            return error(e.getMessage() == null ? "工具执行失败" : e.getMessage());
        }
    }

    private String error(String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("error", message);
        return node.toString();
    }
}

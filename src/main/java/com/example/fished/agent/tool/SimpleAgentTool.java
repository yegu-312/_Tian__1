package com.example.fished.agent.tool;

import com.example.fished.agent.llm.LlmModels;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.BiFunction;
import java.util.function.Function;

public class SimpleAgentTool implements AgentTool {

    private final LlmModels.ToolSpec spec;
    private final boolean readOnly;
    private final BiFunction<JsonNode, ToolContext, String> handler;
    private final Function<JsonNode, String> describer;

    public SimpleAgentTool(LlmModels.ToolSpec spec, boolean readOnly,
                           BiFunction<JsonNode, ToolContext, String> handler,
                           Function<JsonNode, String> describer) {
        this.spec = spec;
        this.readOnly = readOnly;
        this.handler = handler;
        this.describer = describer;
    }

    @Override
    public LlmModels.ToolSpec spec() {
        return spec;
    }

    @Override
    public boolean readOnly() {
        return readOnly;
    }

    @Override
    public String execute(JsonNode args, ToolContext context) {
        return handler.apply(args, context);
    }

    @Override
    public String describe(JsonNode args) {
        if (describer == null) {
            return spec.description();
        }
        try {
            return describer.apply(args);
        } catch (Exception e) {
            return spec.description();
        }
    }
}

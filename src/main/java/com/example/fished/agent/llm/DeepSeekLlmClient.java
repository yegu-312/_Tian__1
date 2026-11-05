package com.example.fished.agent.llm;

import com.example.fished.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * DeepSeek 官方 API 客户端（OpenAI 兼容的 /chat/completions）。
 */
@Component
public class DeepSeekLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekLlmClient.class);

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final AgentProperties properties;

    public DeepSeekLlmClient(RestClient agentRestClient, ObjectMapper mapper, AgentProperties properties) {
        this.restClient = agentRestClient;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public LlmModels.LlmResult stream(LlmModels.ChatRequest request, Consumer<String> onText) {
        if (!properties.isConfigured()) {
            throw new AgentException("未配置 DeepSeek API Key，请设置环境变量 DEEPSEEK_API_KEY");
        }

        String body = buildBody(request).toString();
        MediaType accept = properties.isStream()
                ? MediaType.TEXT_EVENT_STREAM
                : MediaType.APPLICATION_JSON;

        return restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(accept)
                .body(body)
                .exchange((req, res) -> {
                    int status = res.getStatusCode().value();
                    if (status < 200 || status >= 300) {
                        String errorBody = readAll(res.getBody());
                        log.warn("DeepSeek 调用失败：HTTP {}，响应 {}", status, abbreviate(errorBody));
                        throw new AgentException(friendlyStatus(status));
                    }
                    if (!properties.isStream()) {
                        return parseNonStream(res.getBody(), onText);
                    }
                    return parseStream(res.getBody(), onText);
                });
    }

    private ObjectNode buildBody(LlmModels.ChatRequest request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", properties.getModel());
        root.put("stream", properties.isStream());
        root.put("temperature", properties.getTemperature());
        root.put("max_tokens", Math.max(64, properties.getMaxTokens()));

        ArrayNode messages = root.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", request.systemPrompt());

        if (request.messages() != null) {
            for (LlmModels.ChatMessage message : request.messages()) {
                ObjectNode node = messages.addObject();
                node.put("role", message.role());
                if ("assistant".equals(message.role())) {
                    node.put("content", message.content() == null ? "" : message.content());
                    if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                        ArrayNode calls = node.putArray("tool_calls");
                        for (LlmModels.ToolCall call : message.toolCalls()) {
                            ObjectNode callNode = calls.addObject();
                            callNode.put("id", call.id());
                            callNode.put("type", "function");
                            ObjectNode function = callNode.putObject("function");
                            function.put("name", call.name());
                            function.put("arguments", call.arguments());
                        }
                    }
                } else if ("tool".equals(message.role())) {
                    node.put("tool_call_id", message.toolCallId());
                    node.put("content", message.content());
                } else {
                    node.put("content", message.content());
                }
            }
        }

        if (request.tools() != null && !request.tools().isEmpty()) {
            root.put("tool_choice", "auto");
            ArrayNode tools = root.putArray("tools");
            for (LlmModels.ToolSpec spec : request.tools()) {
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                ObjectNode function = tool.putObject("function");
                function.put("name", spec.name());
                function.put("description", spec.description());
                function.set("parameters", mapper.valueToTree(spec.parameters()));
            }
        }
        return root;
    }

    private LlmModels.LlmResult parseStream(InputStream input, Consumer<String> onText) throws java.io.IOException {
        DeepSeekStreamParser parser = new DeepSeekStreamParser(mapper);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring("data:".length()).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if (parser.isDone(data)) {
                    break;
                }
                String delta = parser.accept(data);
                if (!delta.isEmpty() && onText != null) {
                    onText.accept(delta);
                }
            }
        }
        return parser.result();
    }

    private LlmModels.LlmResult parseNonStream(InputStream input, Consumer<String> onText) throws java.io.IOException {
        JsonNode root = mapper.readTree(input);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.size() == 0) {
            throw new AgentException("模型返回了空响应");
        }
        JsonNode choice = choices.get(0);
        JsonNode message = choice.path("message");
        String content = message.path("content").asText("");
        if (!content.isEmpty() && onText != null) {
            onText.accept(content);
        }

        List<LlmModels.ToolCall> calls = new ArrayList<>();
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray()) {
            for (JsonNode call : toolCalls) {
                String id = call.path("id").asText("call_" + (calls.size() + 1));
                JsonNode function = call.path("function");
                String name = function.path("name").asText("");
                String arguments = function.path("arguments").asText("{}");
                if (!name.isBlank()) {
                    calls.add(new LlmModels.ToolCall(id, name, arguments));
                }
            }
        }

        String finishReason = choice.path("finish_reason").asText(
                calls.isEmpty() ? "stop" : "tool_calls");
        return new LlmModels.LlmResult(content, List.copyOf(calls), finishReason);
    }

    private static String readAll(InputStream input) {
        try (InputStream in = input) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300);
    }

    private static String friendlyStatus(int status) {
        return switch (status) {
            case 401 -> "DeepSeek API Key 无效或已过期，请检查 DEEPSEEK_API_KEY";
            case 402 -> "DeepSeek 账户余额不足，请充值后再试";
            case 429 -> "DeepSeek 请求过于频繁，请稍后再试";
            case 500, 502, 503, 504 -> "DeepSeek 服务暂时不可用（HTTP " + status + "），请稍后再试";
            default -> "调用 DeepSeek 失败（HTTP " + status + "）";
        };
    }
}

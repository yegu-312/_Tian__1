package com.example.fished.agent.service;

import com.example.fished.agent.config.AgentProperties;
import com.example.fished.agent.llm.AgentException;
import com.example.fished.agent.llm.LlmClient;
import com.example.fished.agent.llm.LlmModels;
import com.example.fished.agent.tool.AgentTool;
import com.example.fished.agent.tool.ToolContext;
import com.example.fished.agent.tool.ToolRegistry;
import com.example.fished.agent.vo.AgentMessageVO;
import com.example.fished.agent.vo.AgentPendingActionVO;
import com.example.fished.agent.vo.AgentStatusVO;
import com.example.fished.common.BusinessException;
import com.example.fished.sim.PondManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 主循环：模型 -> 工具 -> 模型。写工具先挂起，等用户确认后再执行。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final int MAX_MESSAGE_CHARS = 2000;
    private static final Map<String, String> TOOL_LABELS = Map.ofEntries(
            Map.entry("get_current_time", "查询现实时间"),
            Map.entry("list_ponds", "查询鱼塘列表"),
            Map.entry("get_pond_realtime", "查询实时数据"),
            Map.entry("get_pond_history", "查询历史趋势"),
            Map.entry("compare_ponds", "对比鱼塘"),
            Map.entry("list_alerts", "查询告警"),
            Map.entry("list_aerator_logs", "查询增氧记录"),
            Map.entry("get_aerator_strategy", "查询增氧策略"),
            Map.entry("get_pond_settings", "查询运行设置"),
            Map.entry("get_pond_config", "查询水体参数"),
            Map.entry("get_electricity", "查询电价"),
            Map.entry("predict_do", "预测溶氧"),
            Map.entry("set_manual_aerator", "开关增氧机"),
            Map.entry("update_aerator_strategy", "修改增氧策略"),
            Map.entry("pause_pond", "暂停仿真"),
            Map.entry("resume_pond", "继续仿真")
    );

    private final AgentProperties properties;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ConversationStore conversationStore;
    private final PendingActionStore pendingActionStore;
    private final SystemPrompt systemPrompt;
    private final PondManager pondManager;
    private final ObjectMapper mapper;

    public AgentService(AgentProperties properties,
                        LlmClient llmClient,
                        ToolRegistry toolRegistry,
                        ConversationStore conversationStore,
                        PendingActionStore pendingActionStore,
                        SystemPrompt systemPrompt,
                        PondManager pondManager,
                        ObjectMapper mapper) {
        this.properties = properties;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.conversationStore = conversationStore;
        this.pendingActionStore = pendingActionStore;
        this.systemPrompt = systemPrompt;
        this.pondManager = pondManager;
        this.mapper = mapper;
    }

    public AgentStatusVO status() {
        return new AgentStatusVO(properties.isEnabled(), properties.isConfigured(),
                properties.getProvider(), properties.getModel());
    }

    public List<AgentMessageVO> history(String sessionId, int limit) {
        return conversationStore.get(sessionId).history(Math.max(1, Math.min(100, limit)));
    }

    public void reset(String sessionId) {
        ConversationStore.Conversation conversation = conversationStore.get(sessionId);
        if (!conversation.lock().tryLock()) {
            throw new BusinessException("上一条消息还在处理中，请稍后再试");
        }
        try {
            pendingActionStore.clear(sessionId);
            conversationStore.reset(sessionId);
        } finally {
            conversation.lock().unlock();
        }
    }

    // ------------------------------------------------------------------ 普通对话

    public void chat(String sessionId, String operator, String message, Long pondId, SseEmitter emitter) {
        AgentEventWriter writer = new AgentEventWriter(emitter, mapper);
        ConversationStore.Conversation conversation = conversationStore.get(sessionId);
        if (!conversation.lock().tryLock()) {
            writer.error("上一条消息还在处理中，请稍后再试");
            writer.complete();
            return;
        }
        try {
            if (!ensureAvailable(writer)) {
                return;
            }
            String text = message == null ? "" : message.trim();
            if (text.isEmpty()) {
                writer.error("请输入问题");
                return;
            }
            if (text.length() > MAX_MESSAGE_CHARS) {
                writer.error("问题太长了，请精简到 " + MAX_MESSAGE_CHARS + " 字以内");
                return;
            }
            cancelOutstandingPending(conversation, sessionId);
            conversation.append(LlmModels.ChatMessage.user(text));
            runLoop(sessionId, operator, normalizePondId(pondId), conversation, writer);
        } catch (AgentException e) {
            writer.error(e.getMessage());
        } catch (Exception e) {
            log.error("Agent 对话处理失败", e);
            writer.error("AI 助手暂时不可用，请稍后再试");
        } finally {
            conversation.lock().unlock();
            writer.complete();
        }
    }

    // ------------------------------------------------------------------ 写操作确认

    public void confirm(String sessionId, String operator, String actionId, SseEmitter emitter) {
        AgentEventWriter writer = new AgentEventWriter(emitter, mapper);
        ConversationStore.Conversation conversation = conversationStore.get(sessionId);
        if (!conversation.lock().tryLock()) {
            writer.error("上一条消息还在处理中，请稍后再试");
            writer.complete();
            return;
        }
        try {
            if (!ensureAvailable(writer)) {
                return;
            }
            PendingActionStore.Decision decision = pendingActionStore.take(actionId, sessionId);
            if (!decision.ok()) {
                if (decision.action() != null) {
                    appendCancelled(conversation, decision.action(), decision.error());
                }
                writer.error(decision.error());
                return;
            }
            PendingAction action = decision.action();
            executeWriteAndContinue(sessionId, operator, action, conversation, writer);
        } catch (AgentException e) {
            writer.error(e.getMessage());
        } catch (Exception e) {
            log.error("Agent 写操作执行失败", e);
            writer.error("操作执行失败，请稍后重试");
        } finally {
            conversation.lock().unlock();
            writer.complete();
        }
    }

    public void reject(String sessionId, String actionId) {
        ConversationStore.Conversation conversation = conversationStore.get(sessionId);
        if (!conversation.lock().tryLock()) {
            throw new BusinessException("上一条消息还在处理中，请稍后再试");
        }
        try {
            PendingActionStore.Decision decision = pendingActionStore.take(actionId, sessionId);
            if (decision.action() != null) {
                appendCancelled(conversation, decision.action(), "用户拒绝执行");
            }
            if (!decision.ok()) {
                throw new BusinessException(decision.error());
            }
        } finally {
            conversation.lock().unlock();
        }
    }

    // ------------------------------------------------------------------ 主循环

    private void runLoop(String sessionId, String operator, Long activePondId,
                         ConversationStore.Conversation conversation, AgentEventWriter writer) {
        int maxRounds = Math.max(1, properties.getMaxToolRounds());
        for (int round = 0; round < maxRounds; round++) {
            if (writer.isClosed()) {
                return;
            }
            List<LlmModels.ChatMessage> messages = conversation.snapshot(properties.getMaxHistoryMessages());
            if (messages.isEmpty()) {
                return;
            }
            LlmModels.ChatRequest request = new LlmModels.ChatRequest(
                    systemPrompt.build(activePondId, pondName(activePondId)),
                    messages,
                    toolRegistry.specs());
            LlmModels.LlmResult result = llmClient.stream(request, writer::text);

            List<LlmModels.ToolCall> calls = normalizeCalls(result.toolCalls(), round);
            conversation.append(LlmModels.ChatMessage.assistant(result.content(), calls));

            if (calls.isEmpty()) {
                if (result.content() == null || result.content().isBlank()) {
                    writer.text("这次没有获取到有效回复，请换个问法再试。");
                }
                writer.done("end_turn");
                return;
            }

            LlmModels.ToolCall writeCall = calls.stream()
                    .filter(call -> toolRegistry.isWrite(call.name()))
                    .findFirst()
                    .orElse(null);

            if (writeCall != null) {
                String error = proposeWrite(sessionId, activePondId, conversation, writer, calls, writeCall);
                if (error == null) {
                    writer.done("pending_confirmation");
                    return;
                }
                answerAllWithError(conversation, calls, error);
                continue;
            }

            executeReads(sessionId, operator, activePondId, conversation, writer, calls);
        }

        writer.text("这个问题需要的步骤比较多，我先停在这里。可以把问题拆小一点再问我。");
        writer.done("max_rounds");
    }

    /**
     * 把写工具转成待确认操作。返回 null 表示已挂起；返回文字表示参数校验失败，让模型自己修正。
     */
    private String proposeWrite(String sessionId, Long activePondId,
                                ConversationStore.Conversation conversation, AgentEventWriter writer,
                                List<LlmModels.ToolCall> calls, LlmModels.ToolCall writeCall) {
        AgentTool tool = toolRegistry.get(writeCall.name());
        if (tool == null) {
            return "未知工具：" + writeCall.name();
        }
        JsonNode args;
        try {
            args = parseArgs(writeCall.arguments());
        } catch (BusinessException e) {
            return e.getMessage();
        }
        String description;
        try {
            description = tool.describe(args);
        } catch (Exception e) {
            String message = e.getMessage();
            return message == null || message.isBlank() ? "操作参数无效，请检查鱼塘和阈值" : message;
        }

        for (LlmModels.ToolCall call : calls) {
            if (call.id().equals(writeCall.id())) {
                continue;
            }
            String message = toolRegistry.isWrite(call.name())
                    ? "一次只能确认一个写操作，请先处理当前操作"
                    : "本轮包含写操作，其他查询暂未执行，请在写操作完成后再查询";
            conversation.append(LlmModels.ChatMessage.tool(call.id(), errorJson(message)));
        }

        PendingAction action = pendingActionStore.create(sessionId, writeCall.name(), args, writeCall.id(),
                activePondId, titleOf(writeCall.name()), description);
        writer.pending(AgentPendingActionVO.from(action));
        return null;
    }

    private void executeWriteAndContinue(String sessionId, String operator, PendingAction action,
                                         ConversationStore.Conversation conversation, AgentEventWriter writer) {
        String label = toolLabel(action.toolName());
        writer.toolStart(action.toolName(), label);
        String result;
        try {
            AgentTool tool = toolRegistry.get(action.toolName());
            if (tool == null || tool.readOnly()) {
                result = errorJson("该操作已失效，请重新发起");
            } else {
                result = tool.execute(action.args(), new ToolContext(sessionId, safeOperator(operator), action.activePondId()));
            }
        } catch (Exception e) {
            log.warn("Agent 写工具执行失败：{} - {}", action.toolName(), e.getMessage());
            result = errorJson(e.getMessage() == null ? "操作执行失败" : e.getMessage());
        }
        writer.toolDone(action.toolName(), label, !containsError(result), summarize(result));
        conversation.append(LlmModels.ChatMessage.tool(action.toolCallId(), result));
        runLoop(sessionId, operator, action.activePondId(), conversation, writer);
    }

    // ------------------------------------------------------------------ 工具执行

    private void executeReads(String sessionId, String operator, Long activePondId,
                              ConversationStore.Conversation conversation, AgentEventWriter writer,
                              List<LlmModels.ToolCall> calls) {
        int cap = Math.max(1, properties.getMaxToolsPerRound());
        int executed = 0;
        ToolContext context = new ToolContext(sessionId, safeOperator(operator), activePondId);

        for (LlmModels.ToolCall call : calls) {
            AgentTool tool = toolRegistry.get(call.name());
            if (tool == null) {
                conversation.append(LlmModels.ChatMessage.tool(call.id(), errorJson("未知工具：" + call.name())));
                continue;
            }
            if (!tool.readOnly()) {
                conversation.append(LlmModels.ChatMessage.tool(call.id(), errorJson("写操作需要单独确认")));
                continue;
            }
            if (executed >= cap) {
                conversation.append(LlmModels.ChatMessage.tool(call.id(),
                        errorJson("一次最多执行 " + cap + " 个查询工具，请减少后重试")));
                continue;
            }
            JsonNode args;
            try {
                args = parseArgs(call.arguments());
            } catch (BusinessException e) {
                conversation.append(LlmModels.ChatMessage.tool(call.id(), errorJson(e.getMessage())));
                continue;
            }

            executed++;
            String label = toolLabel(call.name());
            writer.toolStart(call.name(), label);
            String result = toolRegistry.execute(call.name(), args, context);
            writer.toolDone(call.name(), label, !containsError(result), summarize(result));
            conversation.append(LlmModels.ChatMessage.tool(call.id(), result));
        }
    }

    private void answerAllWithError(ConversationStore.Conversation conversation,
                                    List<LlmModels.ToolCall> calls, String message) {
        for (LlmModels.ToolCall call : calls) {
            conversation.append(LlmModels.ChatMessage.tool(call.id(), errorJson(message)));
        }
    }

    private void cancelOutstandingPending(ConversationStore.Conversation conversation, String sessionId) {
        for (PendingAction action : pendingActionStore.cancelAll(sessionId)) {
            appendCancelled(conversation, action, "用户没有确认该操作，已取消");
        }
    }

    private void appendCancelled(ConversationStore.Conversation conversation,
                                 PendingAction action, String reason) {
        conversation.append(LlmModels.ChatMessage.tool(action.toolCallId(), cancelledJson(reason)));
    }

    // ------------------------------------------------------------------ 辅助

    private boolean ensureAvailable(AgentEventWriter writer) {
        if (!properties.isEnabled()) {
            writer.error("AI 助手已关闭");
            return false;
        }
        if (!properties.isConfigured()) {
            writer.error("未配置 DeepSeek API Key，请设置环境变量 DEEPSEEK_API_KEY 后重启应用");
            return false;
        }
        return true;
    }

    private Long normalizePondId(Long pondId) {
        return pondId != null && pondManager.pondIds().contains(pondId) ? pondId : null;
    }

    private String pondName(Long pondId) {
        return pondId == null ? null : pondManager.nameOf(pondId);
    }

    private static String safeOperator(String operator) {
        return operator == null || operator.isBlank() ? "admin" : operator;
    }

    private JsonNode parseArgs(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            JsonNode node = mapper.readTree(arguments);
            if (node == null || !node.isObject()) {
                throw new BusinessException("工具参数必须是一个 JSON 对象");
            }
            return node;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("工具参数不是合法 JSON：" + abbreviate(arguments, 80));
        }
    }

    private static List<LlmModels.ToolCall> normalizeCalls(List<LlmModels.ToolCall> raw, int round) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<LlmModels.ToolCall> calls = new ArrayList<>();
        int index = 0;
        for (LlmModels.ToolCall call : raw) {
            String id = call.id() == null || call.id().isBlank()
                    ? "call_" + round + "_" + index
                    : call.id();
            String name = call.name() == null ? "" : call.name();
            String arguments = call.arguments() == null || call.arguments().isBlank()
                    ? "{}"
                    : call.arguments();
            calls.add(new LlmModels.ToolCall(id, name, arguments));
            index++;
        }
        return List.copyOf(calls);
    }

    private boolean containsError(String json) {
        try {
            return mapper.readTree(json).has("error");
        } catch (Exception e) {
            return false;
        }
    }

    private String summarize(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonNode node = mapper.readTree(json);
            if (node.has("error")) {
                return abbreviate("失败：" + node.get("error").asText(), 140);
            }
            if (node.has("message")) {
                return abbreviate(node.get("message").asText(), 140);
            }
            if (node.has("currentDo")) {
                return "当前溶氧 " + node.get("currentDo").asText() + " mg/L";
            }
            if (node.has("predictedMin")) {
                return "预测最低 " + node.get("predictedMin").asText() + " mg/L";
            }
            if (node.has("count")) {
                return node.get("count").asText() + " 个采样点";
            }
            if (node.has("total")) {
                return "共 " + node.get("total").asText() + " 条记录";
            }
            if (node.has("ponds")) {
                return node.get("ponds").size() + " 口鱼塘";
            }
            if (node.has("onThreshold")) {
                return "开机 " + node.get("onThreshold").asText()
                        + " / 关机 " + node.get("offThreshold").asText() + " mg/L";
            }
            if (node.isArray()) {
                return node.size() + " 条数据";
            }
        } catch (Exception ignored) {
            // 解析失败时退回原始摘要
        }
        return abbreviate(json.replaceAll("\\s+", " "), 140);
    }

    private String errorJson(String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("error", message == null ? "执行失败" : message);
        return node.toString();
    }

    private String cancelledJson(String reason) {
        ObjectNode node = mapper.createObjectNode();
        node.put("cancelled", true);
        node.put("message", reason == null ? "用户未确认该操作" : reason);
        return node.toString();
    }

    private static String toolLabel(String name) {
        return TOOL_LABELS.getOrDefault(name, name == null ? "工具" : name);
    }

    private static String titleOf(String toolName) {
        return switch (toolName) {
            case "set_manual_aerator" -> "开关增氧机";
            case "update_aerator_strategy" -> "修改增氧策略";
            case "pause_pond" -> "暂停仿真";
            case "resume_pond" -> "继续仿真";
            default -> "待确认操作";
        };
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}

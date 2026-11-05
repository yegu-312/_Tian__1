package com.example.fished.agent.service;

import com.example.fished.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 待确认操作：绑定会话、限时、单次使用。
 */
@Component
public class PendingActionStore {

    private final ConcurrentHashMap<String, PendingAction> actions = new ConcurrentHashMap<>();
    private final AgentProperties properties;
    private final ObjectMapper mapper;

    public PendingActionStore(AgentProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public PendingAction create(String sessionId, String toolName, JsonNode args, String toolCallId,
                                Long activePondId, String title, String description) {
        cleanupExpired();
        String id = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now()
                .plusSeconds(Math.max(1, properties.getPendingActionTtlSeconds()));
        PendingAction action = new PendingAction(
                id,
                sessionId,
                toolName,
                args == null ? mapper.createObjectNode() : args.deepCopy(),
                toolCallId,
                activePondId,
                title,
                description,
                expiresAt);
        actions.put(id, action);
        return action;
    }

    /**
     * 原子地取出并作废一个操作。
     * 过期时仍返回 action，方便调用方补上对应的 tool 结果，保持对话历史合法。
     */
    public Decision take(String id, String sessionId) {
        PendingAction action = id == null ? null : actions.get(id);
        if (action == null) {
            return new Decision(null, "该操作已失效，请重新发起");
        }
        if (sessionId == null || !sessionId.equals(action.sessionId())) {
            return new Decision(null, "无权确认该操作");
        }
        if (Instant.now().isAfter(action.expiresAt())) {
            actions.remove(id);
            return new Decision(action, "该操作已过期，请重新发起");
        }
        if (!actions.remove(id, action)) {
            return new Decision(null, "该操作已被处理，请勿重复确认");
        }
        return new Decision(action, null);
    }

    /** action 非空表示找到了操作；error 非空表示操作不可执行。 */
    public record Decision(PendingAction action, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    /** 用户发起新消息时，把还没确认的操作全部作废并返回，便于补上 tool 结果。 */
    public List<PendingAction> cancelAll(String sessionId) {
        List<PendingAction> cancelled = new ArrayList<>();
        for (PendingAction action : actions.values()) {
            if (action.sessionId().equals(sessionId) && actions.remove(action.id(), action)) {
                cancelled.add(action);
            }
        }
        return cancelled;
    }

    public void clear(String sessionId) {
        actions.entrySet().removeIf(entry -> entry.getValue().sessionId().equals(sessionId));
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        actions.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }
}

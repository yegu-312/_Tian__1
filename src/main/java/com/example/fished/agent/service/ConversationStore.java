package com.example.fished.agent.service;

import com.example.fished.agent.config.AgentProperties;
import com.example.fished.agent.llm.LlmModels;
import com.example.fished.agent.vo.AgentMessageVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 按 HttpSession id 保存对话。只存在内存中，重启即清空。
 * 同一会话用独立锁串行处理，避免两条消息同时改写历史。
 */
@Component
public class ConversationStore {

    private static final long IDLE_MILLIS = 2 * 60 * 60 * 1000L;
    private static final long CLEANUP_INTERVAL_MILLIS = 5 * 60 * 1000L;
    private static final int MAX_CONVERSATIONS = 200;
    private static final int MAX_RAW_MESSAGES = 200;

    private final AgentProperties properties;
    private final ConcurrentHashMap<String, Conversation> conversations = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    public ConversationStore(AgentProperties properties) {
        this.properties = properties;
    }

    public Conversation get(String sessionId) {
        cleanupIfNeeded();
        Conversation conversation = conversations.computeIfAbsent(sessionId, key -> new Conversation());
        conversation.touch();
        return conversation;
    }

    public void reset(String sessionId) {
        conversations.remove(sessionId);
    }

    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MILLIS && conversations.size() <= MAX_CONVERSATIONS) {
            return;
        }
        lastCleanup = now;
        conversations.entrySet().removeIf(entry ->
                now - entry.getValue().lastAccess() > IDLE_MILLIS);
        if (conversations.size() <= MAX_CONVERSATIONS) {
            return;
        }
        conversations.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastAccess()))
                .limit(conversations.size() - MAX_CONVERSATIONS)
                .map(java.util.Map.Entry::getKey)
                .toList()
                .forEach(conversations::remove);
    }

    public static final class Conversation {

        private final ReentrantLock lock = new ReentrantLock();
        private final List<LlmModels.ChatMessage> messages = new ArrayList<>();
        private volatile long lastAccess = System.currentTimeMillis();

        public ReentrantLock lock() {
            return lock;
        }

        public void touch() {
            lastAccess = System.currentTimeMillis();
        }

        public long lastAccess() {
            return lastAccess;
        }

        public synchronized void append(LlmModels.ChatMessage message) {
            messages.add(message);
            trimRaw();
            touch();
        }

        /**
         * 裁剪到最近 maxMessages 条，并从一条 user 消息开始，避免出现孤立的 tool 结果。
         * 若最后一条 assistant 的 tool_calls 还没有全部结果（比如待确认操作已过期），
         * 暂时从上下文中去掉这一组，保证发给模型的消息序列合法。
         */
        public synchronized List<LlmModels.ChatMessage> snapshot(int maxMessages) {
            int end = completeTailEnd();
            int start = Math.max(0, end - Math.max(1, maxMessages));
            while (start < end && !"user".equals(messages.get(start).role())) {
                start++;
            }
            if (start >= end) {
                return List.of();
            }
            return List.copyOf(messages.subList(start, end));
        }

        private int completeTailEnd() {
            int lastAssistant = -1;
            for (int i = messages.size() - 1; i >= 0; i--) {
                LlmModels.ChatMessage message = messages.get(i);
                if ("assistant".equals(message.role())
                        && message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                    lastAssistant = i;
                    break;
                }
            }
            if (lastAssistant < 0) {
                return messages.size();
            }
            java.util.Set<String> answered = new java.util.HashSet<>();
            for (int i = lastAssistant + 1; i < messages.size(); i++) {
                LlmModels.ChatMessage message = messages.get(i);
                if ("tool".equals(message.role()) && message.toolCallId() != null) {
                    answered.add(message.toolCallId());
                }
            }
            for (LlmModels.ToolCall call : messages.get(lastAssistant).toolCalls()) {
                if (!answered.contains(call.id())) {
                    return lastAssistant;
                }
            }
            return messages.size();
        }

        public synchronized List<AgentMessageVO> history(int limit) {
            List<AgentMessageVO> result = new ArrayList<>();
            for (LlmModels.ChatMessage message : messages) {
                if ("user".equals(message.role()) && message.content() != null) {
                    result.add(new AgentMessageVO("user", message.content()));
                } else if ("assistant".equals(message.role())
                        && message.content() != null && !message.content().isBlank()) {
                    result.add(new AgentMessageVO("assistant", message.content()));
                }
            }
            int from = Math.max(0, result.size() - Math.max(1, limit));
            return List.copyOf(result.subList(from, result.size()));
        }

        public synchronized void clear() {
            messages.clear();
            touch();
        }

        public synchronized int size() {
            return messages.size();
        }

        private void trimRaw() {
            while (messages.size() > MAX_RAW_MESSAGES) {
                messages.remove(0);
            }
            int start = 0;
            while (start < messages.size() && !"user".equals(messages.get(start).role())) {
                start++;
            }
            if (start > 0) {
                messages.subList(0, start).clear();
            }
        }
    }
}

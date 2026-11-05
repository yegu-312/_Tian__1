package com.example.fished.agent.service;

import com.example.fished.agent.config.AgentProperties;
import com.example.fished.agent.llm.LlmModels;
import com.example.fished.agent.vo.AgentMessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationStoreTest {

    private ConversationStore store;

    @BeforeEach
    void setUp() {
        store = new ConversationStore(new AgentProperties());
    }

    @Test
    @DisplayName("不同会话彼此隔离，reset 后得到全新会话")
    void sessionsAreIsolated() {
        ConversationStore.Conversation first = store.get("s1");
        first.append(LlmModels.ChatMessage.user("一号塘怎么样"));

        ConversationStore.Conversation second = store.get("s2");
        assertNotSame(first, second);
        assertTrue(second.snapshot(10).isEmpty());

        store.reset("s1");
        assertNotSame(first, store.get("s1"));
    }

    @Test
    @DisplayName("裁剪时从一条 user 消息开始，避免孤立 tool 结果")
    void snapshotStartsAtUserMessage() {
        ConversationStore.Conversation conversation = store.get("s1");
        conversation.append(LlmModels.ChatMessage.user("q1"));
        conversation.append(LlmModels.ChatMessage.assistant("a1", List.of()));
        conversation.append(LlmModels.ChatMessage.user("q2"));
        conversation.append(LlmModels.ChatMessage.assistant("a2", List.of()));

        List<LlmModels.ChatMessage> snapshot = conversation.snapshot(3);
        assertEquals(2, snapshot.size());
        assertEquals("q2", snapshot.get(0).content());
        assertEquals("a2", snapshot.get(1).content());
    }

    @Test
    @DisplayName("末尾 tool_calls 缺少结果时整组从上下文剔除")
    void excludesIncompleteToolCallTail() {
        ConversationStore.Conversation conversation = store.get("tail");
        conversation.append(LlmModels.ChatMessage.user("看一下一号塘"));
        conversation.append(LlmModels.ChatMessage.assistant("", List.of(
                new LlmModels.ToolCall("call_1", "get_pond_realtime", "{\"pondId\":1}"))));

        List<LlmModels.ChatMessage> before = conversation.snapshot(30);
        assertEquals(1, before.size());
        assertEquals("user", before.get(0).role());

        conversation.append(LlmModels.ChatMessage.tool("call_1", "{\"currentDo\":5.8}"));
        List<LlmModels.ChatMessage> after = conversation.snapshot(30);
        assertEquals(3, after.size());
        assertEquals("tool", after.get(2).role());
    }

    @Test
    @DisplayName("历史只返回 user 和正文非空的 assistant")
    void historySkipsToolMessages() {
        ConversationStore.Conversation conversation = store.get("s1");
        conversation.append(LlmModels.ChatMessage.user("你好"));
        conversation.append(LlmModels.ChatMessage.assistant("", List.of(
                new LlmModels.ToolCall("call_1", "list_ponds", "{}"))));
        conversation.append(LlmModels.ChatMessage.tool("call_1", "[\"一号塘\"]"));
        conversation.append(LlmModels.ChatMessage.assistant("目前有一口塘。", List.of()));

        List<AgentMessageVO> history = conversation.history(10);
        assertEquals(2, history.size());
        assertEquals("user", history.get(0).role());
        assertEquals("你好", history.get(0).content());
        assertEquals("assistant", history.get(1).role());
        assertEquals("目前有一口塘。", history.get(1).content());
    }

    @Test
    @DisplayName("history(limit) 只保留最近若干条文本消息")
    void historyRespectsLimit() {
        ConversationStore.Conversation conversation = store.get("s1");
        for (int i = 1; i <= 5; i++) {
            conversation.append(LlmModels.ChatMessage.user("q" + i));
            conversation.append(LlmModels.ChatMessage.assistant("a" + i, List.of()));
        }

        List<AgentMessageVO> history = conversation.history(4);
        assertEquals(4, history.size());
        assertEquals("q4", history.get(0).content());
        assertEquals("a5", history.get(3).content());
    }
}

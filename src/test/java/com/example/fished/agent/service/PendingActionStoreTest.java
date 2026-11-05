package com.example.fished.agent.service;

import com.example.fished.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PendingActionStoreTest {

    private AgentProperties properties;
    private PendingActionStore store;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        properties = new AgentProperties();
        properties.setPendingActionTtlSeconds(300);
        mapper = new ObjectMapper();
        store = new PendingActionStore(properties, mapper);
    }

    private PendingAction create(String sessionId, String toolName) throws Exception {
        return store.create(sessionId, toolName,
                mapper.readTree("{\"pondId\":1,\"on\":true}"),
                "call_1", 1L, "开启一号塘增氧机", "将一号塘切换为手动并开启增氧机");
    }

    @Test
    @DisplayName("创建后可被同一会话取出一次")
    void takeOnce() throws Exception {
        PendingAction action = create("s1", "set_manual_aerator");

        PendingActionStore.Decision first = store.take(action.id(), "s1");
        assertTrue(first.ok());
        assertNotNull(first.action());
        assertEquals("set_manual_aerator", first.action().toolName());
        assertTrue(first.action().args().get("on").asBoolean());

        PendingActionStore.Decision second = store.take(action.id(), "s1");
        assertFalse(second.ok());
        assertNull(second.action());
        assertTrue(second.error().contains("已失效"));
    }

    @Test
    @DisplayName("其他会话不能确认，也不能消耗该操作")
    void sessionMismatchDoesNotConsume() throws Exception {
        PendingAction action = create("s1", "set_manual_aerator");

        PendingActionStore.Decision denied = store.take(action.id(), "s2");
        assertFalse(denied.ok());
        assertNull(denied.action());
        assertTrue(denied.error().contains("无权"));

        assertTrue(store.take(action.id(), "s1").ok());
    }

    @Test
    @DisplayName("过期操作不可执行，但会返回 action 便于补历史")
    void expiredAction() throws Exception {
        properties.setPendingActionTtlSeconds(1);
        PendingAction action = create("s1", "pause_pond");

        Thread.sleep(1200);

        PendingActionStore.Decision decision = store.take(action.id(), "s1");
        assertFalse(decision.ok());
        assertNotNull(decision.action());
        assertTrue(decision.error().contains("过期"));
        // 已从存储中移除
        assertNull(store.take(action.id(), "s1").action());
    }

    @Test
    @DisplayName("cancelAll 只作废当前会话的待确认操作")
    void cancelAllIsSessionScoped() throws Exception {
        create("s1", "pause_pond");
        create("s1", "resume_pond");
        PendingAction other = create("s2", "pause_pond");

        List<PendingAction> cancelled = store.cancelAll("s1");
        assertEquals(2, cancelled.size());
        assertTrue(cancelled.stream().allMatch(action -> "s1".equals(action.sessionId())));

        assertTrue(store.take(other.id(), "s2").ok());
    }

    @Test
    @DisplayName("参数在创建时深拷贝，外部修改不影响待确认内容")
    void freezesArguments() throws Exception {
        var args = mapper.readTree("{\"pondId\":1}");
        PendingAction action = store.create("s1", "pause_pond", args, "call_1", 1L, "暂停", "暂停一号塘");
        ((com.fasterxml.jackson.databind.node.ObjectNode) args).put("pondId", 99);
        assertEquals(1, action.args().get("pondId").asInt());
    }
}

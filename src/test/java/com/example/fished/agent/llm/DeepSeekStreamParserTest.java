package com.example.fished.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekStreamParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("正文增量按顺序拼接，并逐段返回")
    void accumulatesTextDeltas() {
        DeepSeekStreamParser parser = new DeepSeekStreamParser(mapper);

        assertEquals("一号塘", parser.accept(
                "{\"choices\":[{\"delta\":{\"content\":\"一号塘\"}}]}"));
        assertEquals("当前溶氧", parser.accept(
                "{\"choices\":[{\"delta\":{\"content\":\"当前溶氧\"}}]}"));
        assertTrue(parser.accept("{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}").isEmpty());

        LlmModels.LlmResult result = parser.result();
        assertEquals("一号塘当前溶氧", result.content());
        assertEquals("stop", result.finishReason());
        assertTrue(result.toolCalls().isEmpty());
    }

    @Test
    @DisplayName("工具调用按 index 聚合，参数分片拼接")
    void aggregatesToolCallFragments() {
        DeepSeekStreamParser parser = new DeepSeekStreamParser(mapper);

        parser.accept("""
                {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1",
                "function":{"name":"get_pond_realtime","arguments":"{\\"pondId\\":"}}]}}]}
                """.replace("\n", ""));
        parser.accept("""
                {"choices":[{"delta":{"tool_calls":[{"index":0,
                "function":{"arguments":"1,\\"predictMinutes\\":30}"}}]}}]}
                """.replace("\n", ""));
        parser.accept("""
                {"choices":[{"delta":{"tool_calls":[{"index":1,"id":"call_2",
                "function":{"name":"list_alerts","arguments":"{\\"limit\\":5}"}}]},
                "finish_reason":"tool_calls"}]}
                """.replace("\n", ""));

        LlmModels.LlmResult result = parser.result();
        List<LlmModels.ToolCall> calls = result.toolCalls();
        assertEquals(2, calls.size());
        assertEquals("call_1", calls.get(0).id());
        assertEquals("get_pond_realtime", calls.get(0).name());
        assertEquals("{\"pondId\":1,\"predictMinutes\":30}", calls.get(0).arguments());
        assertEquals("list_alerts", calls.get(1).name());
        assertEquals("{\"limit\":5}", calls.get(1).arguments());
        assertEquals("tool_calls", result.finishReason());
    }

    @Test
    @DisplayName("缺少 id 时生成稳定的兜底 id")
    void generatesFallbackId() {
        DeepSeekStreamParser parser = new DeepSeekStreamParser(mapper);
        parser.accept("""
                {"choices":[{"delta":{"tool_calls":[{"index":0,
                "function":{"name":"list_ponds","arguments":"{}"}}]}}]}
                """.replace("\n", ""));

        LlmModels.ToolCall call = parser.result().toolCalls().get(0);
        assertEquals("call_1", call.id());
        assertEquals("{}", call.arguments());
    }

    @Test
    @DisplayName("非法 JSON 抛出 AgentException")
    void rejectsMalformedChunk() {
        DeepSeekStreamParser parser = new DeepSeekStreamParser(mapper);
        assertThrows(AgentException.class, () -> parser.accept("{not-json"));
    }

    @Test
    @DisplayName("忽略空行和空增量")
    void ignoresEmptyData() {
        DeepSeekStreamParser parser = new DeepSeekStreamParser(mapper);
        assertEquals("", parser.accept(""));
        assertEquals("", parser.accept("   "));
        assertEquals("", parser.result().content());
    }
}

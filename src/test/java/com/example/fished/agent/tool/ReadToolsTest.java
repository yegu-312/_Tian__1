package com.example.fished.agent.tool;

import com.example.fished.common.BusinessException;
import com.example.fished.service.AeratorLogService;
import com.example.fished.service.AeratorStrategyService;
import com.example.fished.service.AlertService;
import com.example.fished.service.ElectricityPriceService;
import com.example.fished.service.PondConfigService;
import com.example.fished.service.SimSettingService;
import com.example.fished.sim.PondManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ReadToolsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private List<AgentTool> tools;

    @BeforeEach
    void setUp() {
        tools = new ReadTools(
                mock(PondManager.class),
                mock(AlertService.class),
                mock(AeratorLogService.class),
                mock(AeratorStrategyService.class),
                mock(SimSettingService.class),
                mock(PondConfigService.class),
                mock(ElectricityPriceService.class),
                mapper).all();
    }

    private AgentTool tool(String name) {
        return tools.stream()
                .filter(candidate -> candidate.spec().name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("默认返回服务器时区的现实时间，字段完整可解析")
    void returnsCurrentTimeInServerZone() throws Exception {
        AgentTool time = tool("get_current_time");
        assertTrue(time.readOnly());

        JsonNode result = mapper.readTree(time.execute(mapper.createObjectNode(), new ToolContext("s1", "alice", 1L)));

        assertEquals(ZoneId.systemDefault().getId(), result.get("timezone").asText());
        assertTrue(result.get("date").asText().matches("\\d{4}-\\d{2}-\\d{2}"));
        assertTrue(result.get("time").asText().matches("\\d{2}:\\d{2}:\\d{2}"));
        assertTrue(result.get("weekday").asText().startsWith("星期"));
        assertTrue(result.get("epochMillis").asLong() > 0);
        assertDoesNotThrow(() -> ZonedDateTime.parse(result.get("datetime").asText()));
        assertTrue(result.get("note").asText().contains("不是鱼塘仿真时间"));
    }

    @Test
    @DisplayName("支持指定 IANA 时区")
    void supportsTimezone() throws Exception {
        AgentTool time = tool("get_current_time");
        JsonNode args = mapper.readTree("{\"timezone\":\"UTC\"}");

        JsonNode result = mapper.readTree(time.execute(args, new ToolContext("s1", "alice", 1L)));

        assertEquals("UTC", result.get("timezone").asText());
        assertTrue(result.get("datetime").asText().endsWith("Z"));
    }

    @Test
    @DisplayName("非法时区给出可执行的错误提示")
    void rejectsInvalidTimezone() throws Exception {
        AgentTool time = tool("get_current_time");
        JsonNode args = mapper.readTree("{\"timezone\":\"Mars/Olympus\"}");

        BusinessException error = assertThrows(BusinessException.class,
                () -> time.execute(args, new ToolContext("s1", "alice", 1L)));
        assertTrue(error.getMessage().contains("时区"));
    }
}

package com.example.fished.agent.tool;

import com.example.fished.common.BusinessException;
import com.example.fished.vo.HistorySeriesVO;
import com.example.fished.service.AeratorLogService;
import com.example.fished.service.AeratorStrategyService;
import com.example.fished.service.AlertService;
import com.example.fished.service.ElectricityPriceService;
import com.example.fished.service.HistoryService;
import com.example.fished.service.PondConfigService;
import com.example.fished.service.SimSettingService;
import com.example.fished.sim.DoPoint;
import com.example.fished.sim.PondManager;
import com.example.fished.sim.PondSnapshot;
import com.example.fished.sim.SimulationEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadToolsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HistoryService historyService = mock(HistoryService.class);
    private final PondManager pondManager = mock(PondManager.class);
    private List<AgentTool> tools;

    @BeforeEach
    void setUp() {
        tools = new ReadTools(
                pondManager,
                mock(AlertService.class),
                mock(AeratorLogService.class),
                mock(AeratorStrategyService.class),
                mock(SimSettingService.class),
                mock(PondConfigService.class),
                mock(ElectricityPriceService.class),
                historyService,
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
        assertTrue(result.get("note").asText().contains("不是鱼塘时间"));
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

    @Test
    @DisplayName("按日期范围查询落库历史并返回摘要与趋势点")
    void queriesPersistedHistory() throws Exception {
        HistorySeriesVO series = new HistorySeriesVO();
        series.setPondId(1L);
        series.setPondName("一号塘");
        series.setFrom(LocalDateTime.of(2026, 11, 5, 0, 0));
        series.setTo(LocalDateTime.of(2026, 11, 6, 0, 0));
        series.setCurrentPondTime(LocalDateTime.of(2026, 11, 6, 8, 0));
        series.setRetentionDays(31);
        series.setBucketMinutes(5);
        series.setSampleCount(288);
        series.setSummary(new HistorySeriesVO.Summary(
                5.5, 4.2, 6.8, 24.0, 23.0, 27.0, 30, 5, 4.0, 6.0, 2.5));
        series.setPoints(List.of(
                new HistorySeriesVO.Point(LocalDateTime.of(2026, 11, 5, 0, 0), 5.1, 23.5, false),
                new HistorySeriesVO.Point(LocalDateTime.of(2026, 11, 5, 0, 5), 5.2, 23.6, true)));
        when(historyService.series(any())).thenReturn(series);

        AgentTool history = tool("query_pond_history");
        assertTrue(history.readOnly());

        JsonNode args = mapper.readTree("{\"pondId\":1,\"from\":\"2026-11-05\",\"to\":\"2026-11-05\"}");
        JsonNode result = mapper.readTree(history.execute(args, new ToolContext("s1", "alice", 1L)));

        assertEquals("一号塘", result.get("pondName").asText());
        assertEquals(31, result.get("retentionDays").asInt());
        assertEquals(5.5, result.get("summary").get("avgDo").asDouble(), 0.001);
        assertEquals(30, result.get("summary").get("aeratorMinutes").asLong());
        assertEquals(2, result.get("points").size());
        assertTrue(result.get("note").asText().contains("31 天"));
    }

    @Test
    @DisplayName("测量 pH 返回当前值、状态和最近窗口")
    void measuresPh() throws Exception {
        SimulationEngine engine = mock(SimulationEngine.class);
        when(pondManager.pondIds()).thenReturn(List.of(1L));
        when(pondManager.requireEngine(1L)).thenReturn(engine);
        when(pondManager.nameOf(1L)).thenReturn("一号塘");
        when(engine.getSnapshot()).thenReturn(new PondSnapshot(
                LocalDateTime.of(2026, 11, 15, 10, 0), 5.6, 25.0, false, false, 1, 240, 8.72));
        when(engine.getHistory(120)).thenReturn(List.of(
                new DoPoint(0, 5.4, 25.0, false, 7.35),
                new DoPoint(30, 5.6, 25.0, false, 8.72)));

        AgentTool measure = tool("measure_ph");
        assertTrue(measure.readOnly());

        JsonNode result = mapper.readTree(measure.execute(
                mapper.readTree("{\"pondId\":1}"), new ToolContext("s1", "alice", 1L)));

        assertEquals("一号塘", result.get("pondName").asText());
        assertEquals(8.72, result.get("ph").asDouble(), 0.001);
        assertEquals("偏碱", result.get("status").asText());
        assertEquals(6.5, result.get("comfortMin").asDouble(), 0.001);
        assertEquals(8.5, result.get("comfortMax").asDouble(), 0.001);
        assertEquals(7.35, result.get("recentMin").asDouble(), 0.001);
        assertEquals(8.72, result.get("recentMax").asDouble(), 0.001);
        assertEquals(1.37, result.get("recentChange").asDouble(), 0.001);
        assertEquals(2, result.get("recentSamples").asInt());
        assertTrue(result.get("note").asText().contains("不参与自动增氧决策"));
    }
}

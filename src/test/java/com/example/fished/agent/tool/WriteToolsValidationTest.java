package com.example.fished.agent.tool;

import com.example.fished.common.BusinessException;
import com.example.fished.common.enums.OperationActionEnum;
import com.example.fished.common.enums.OperationModuleEnum;
import com.example.fished.dto.StrategyUpdateDTO;
import com.example.fished.service.AeratorStrategyService;
import com.example.fished.service.OperationLogService;
import com.example.fished.sim.PondManager;
import com.example.fished.sim.PondSnapshot;
import com.example.fished.sim.SimulationEngine;
import com.example.fished.vo.StrategyVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WriteToolsValidationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private PondManager pondManager;
    private AeratorStrategyService strategyService;
    private OperationLogService operationLogService;
    private SimulationEngine engine;
    private List<AgentTool> tools;

    @BeforeEach
    void setUp() {
        pondManager = mock(PondManager.class);
        strategyService = mock(AeratorStrategyService.class);
        operationLogService = mock(OperationLogService.class);
        engine = mock(SimulationEngine.class);

        when(pondManager.requireEngine(anyLong())).thenReturn(engine);
        when(pondManager.nameOf(anyLong())).thenReturn("一号塘");
        when(strategyService.getStrategy(anyLong())).thenReturn(currentStrategy());
        when(strategyService.updateStrategy(anyLong(), any(StrategyUpdateDTO.class), anyString()))
                .thenReturn(currentStrategy());
        when(strategyService.switchManual(anyLong(), anyBoolean(), anyString()))
                .thenReturn(currentStrategy());

        tools = new WriteTools(pondManager, strategyService, operationLogService, mapper).all();
    }

    private AgentTool tool(String name) {
        return tools.stream()
                .filter(candidate -> candidate.spec().name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private JsonNode args(String json) throws Exception {
        return mapper.readTree(json);
    }

    private static ToolContext context() {
        return new ToolContext("session-1", "alice", 1L);
    }

    private static StrategyVO currentStrategy() {
        StrategyVO vo = new StrategyVO();
        vo.setPondId(1L);
        vo.setMode("auto");
        vo.setOnThreshold(BigDecimal.valueOf(5));
        vo.setOffThreshold(BigDecimal.valueOf(6));
        vo.setSafetyFloor(BigDecimal.valueOf(4));
        vo.setPredictMinutes(10);
        vo.setManualOn(false);
        return vo;
    }

    @Test
    @DisplayName("非法模式被拒绝")
    void rejectsUnknownMode() throws Exception {
        BusinessException error = assertThrows(BusinessException.class,
                () -> tool("update_aerator_strategy").execute(args("{\"pondId\":1,\"mode\":\"robot\"}"), context()));
        assertTrue(error.getMessage().contains("控制模式"));
    }

    @Test
    @DisplayName("关机阈值必须大于开机阈值")
    void rejectsOffNotGreaterThanOn() throws Exception {
        BusinessException error = assertThrows(BusinessException.class,
                () -> tool("update_aerator_strategy")
                        .execute(args("{\"pondId\":1,\"onThreshold\":7,\"offThreshold\":7}"), context()));
        assertTrue(error.getMessage().contains("关机阈值"));
    }

    @Test
    @DisplayName("安全下限不能高于开机阈值")
    void rejectsSafetyFloorAboveOn() throws Exception {
        BusinessException error = assertThrows(BusinessException.class,
                () -> tool("update_aerator_strategy")
                        .execute(args("{\"pondId\":1,\"safetyFloor\":9}"), context()));
        assertTrue(error.getMessage().contains("安全下限"));
    }

    @Test
    @DisplayName("阈值超出 0-20 被拒绝")
    void rejectsOutOfRangeThreshold() throws Exception {
        BusinessException error = assertThrows(BusinessException.class,
                () -> tool("update_aerator_strategy")
                        .execute(args("{\"pondId\":1,\"onThreshold\":25}"), context()));
        assertTrue(error.getMessage().contains("0-20"));
    }

    @Test
    @DisplayName("只改一个字段时其余字段沿用当前策略")
    void mergesWithCurrentStrategy() throws Exception {
        tool("update_aerator_strategy").execute(args("{\"pondId\":1,\"onThreshold\":4.5}"), context());

        ArgumentCaptor<StrategyUpdateDTO> captor = ArgumentCaptor.forClass(StrategyUpdateDTO.class);
        verify(strategyService).updateStrategy(eq(1L), captor.capture(), eq("alice"));
        StrategyUpdateDTO dto = captor.getValue();
        assertEquals("auto", dto.getMode());
        assertEquals(0, dto.getOnThreshold().compareTo(new BigDecimal("4.5")));
        assertEquals(0, dto.getOffThreshold().compareTo(new BigDecimal("6")));
        assertEquals(0, dto.getSafetyFloor().compareTo(new BigDecimal("4")));
        assertEquals(10, dto.getPredictMinutes());
        assertFalse(dto.getManualOn());
    }

    @Test
    @DisplayName("手动开关使用会话操作人执行")
    void manualSwitchUsesSessionOperator() throws Exception {
        String result = tool("set_manual_aerator")
                .execute(args("{\"pondId\":1,\"on\":true}"), context());

        verify(strategyService).switchManual(1L, true, "alice");
        assertTrue(result.contains("增氧机已开启"));
    }

    @Test
    @DisplayName("暂停仿真同时写审计日志")
    void pauseWritesAuditLog() throws Exception {
        when(engine.pause()).thenReturn(new PondSnapshot(
                LocalDateTime.of(2026, 11, 5, 9, 30), 5.8, 24.1, true, true, 1, 120));

        tool("pause_pond").execute(args("{\"pondId\":1}"), context());

        verify(engine).pause();
        verify(operationLogService).record(eq(OperationModuleEnum.CLOCK),
                eq(OperationActionEnum.PAUSE), contains("暂停仿真"), eq("alice"));
    }

    @Test
    @DisplayName("确认卡片文案包含塘名和参数变化")
    void describeIncludesPondAndChange() throws Exception {
        String manual = tool("set_manual_aerator").describe(args("{\"pondId\":1,\"on\":true}"));
        assertTrue(manual.contains("开启一号塘增氧机"));

        String update = tool("update_aerator_strategy").describe(args("{\"pondId\":1,\"onThreshold\":4.5}"));
        assertTrue(update.contains("一号塘"));
        assertTrue(update.contains("开机阈值 5 → 4.5 mg/L"));
    }

    @Test
    @DisplayName("缺少 pondId 时给出可执行的提示")
    void requiresPondId() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> tool("pause_pond").execute(mapper.createObjectNode(), context()));
        assertTrue(error.getMessage().contains("pondId"));
    }
}

package com.example.fished.sim;

import com.example.fished.common.enums.AlertLevelEnum;
import com.example.fished.common.enums.AlertTypeEnum;
import com.example.fished.sim.event.AlertRaisedEvent;
import com.example.fished.vo.TemperatureStatusVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TemperatureGuardTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 11, 9, 14, 0);

    @Test
    @DisplayName("水温在舒适区间内判定为适宜")
    void normalInsideComfortRange() {
        assertEquals(TemperatureGuard.Status.NORMAL, TemperatureGuard.status(18.0));
        assertEquals(TemperatureGuard.Status.NORMAL, TemperatureGuard.status(25.0));
        assertEquals(TemperatureGuard.Status.NORMAL, TemperatureGuard.status(32.0));
    }

    @Test
    @DisplayName("水温高于上限或低于下限时判定为偏高/偏低")
    void highAndLowOutsideComfortRange() {
        assertEquals(TemperatureGuard.Status.HIGH, TemperatureGuard.status(32.1));
        assertEquals(TemperatureGuard.Status.LOW, TemperatureGuard.status(17.9));
        assertTrue(TemperatureGuard.isHigh(35.0));
        assertTrue(TemperatureGuard.isLow(15.0));
        assertFalse(TemperatureGuard.isHigh(30.0));
        assertFalse(TemperatureGuard.isLow(20.0));
    }

    @Test
    @DisplayName("NaN 不误报，按适宜处理")
    void nanTreatedAsNormal() {
        assertEquals(TemperatureGuard.Status.NORMAL, TemperatureGuard.status(Double.NaN));
    }

    @Test
    @DisplayName("回差：高温需回到上限减 0.5 才算恢复，低温需回到下限加 0.5")
    void hysteresisRecoveryBoundaries() {
        assertFalse(TemperatureGuard.highRecovered(31.9), "31.9 仍在回差带内，不应解除告警");
        assertTrue(TemperatureGuard.highRecovered(31.5), "回到 31.5 才解除高温告警");
        assertFalse(TemperatureGuard.lowRecovered(18.2), "18.2 仍在回差带内，不应解除告警");
        assertTrue(TemperatureGuard.lowRecovered(18.5), "回到 18.5 才解除低温告警");
    }

    @Test
    @DisplayName("温度状态 VO 下发统一阈值与中文描述")
    void statusVoCarriesThresholds() {
        TemperatureStatusVO high = TemperatureStatusVO.from(35.0);
        assertEquals(18.0, high.getComfortMin());
        assertEquals(32.0, high.getComfortMax());
        assertEquals("high", high.getStatus());
        assertEquals("偏高", high.getStatusDesc());

        TemperatureStatusVO normal = TemperatureStatusVO.from(25.0);
        assertEquals("normal", normal.getStatus());
        assertEquals("适宜", normal.getStatusDesc());
    }

    @Test
    @DisplayName("高温告警：类型正确、消息含水温、不携带溶氧值")
    void temperatureHighAlertEvent() {
        AlertRaisedEvent event = AlertRaisedEvent.temperatureHigh(1L, TIME, 33.4);
        assertEquals(AlertTypeEnum.TEMPERATURE_HIGH, event.alertType());
        assertEquals(AlertLevelEnum.WARN, event.level());
        assertNull(event.doValue(), "温度告警没有溶氧值");
        assertTrue(event.message().contains("33.4"));
        assertTrue(event.message().contains("32.0"));
    }

    @Test
    @DisplayName("低温告警：类型正确、消息含水温、不携带溶氧值")
    void temperatureLowAlertEvent() {
        AlertRaisedEvent event = AlertRaisedEvent.temperatureLow(2L, TIME, 16.5);
        assertEquals(AlertTypeEnum.TEMPERATURE_LOW, event.alertType());
        assertEquals(AlertLevelEnum.WARN, event.level());
        assertNull(event.doValue(), "温度告警没有溶氧值");
        assertTrue(event.message().contains("16.5"));
        assertTrue(event.message().contains("18.0"));
    }
}

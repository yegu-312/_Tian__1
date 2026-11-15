package com.example.fished.service.impl;

import com.example.fished.entity.AeratorLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ElectricityCostCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);
    private static final LocalDateTime DAY_START = DAY.atStartOfDay();

    @Test
    @DisplayName("完整开机区间按小时累加电量与电费")
    void calculatesSimpleOnOffInterval() {
        List<BigDecimal> prices = uniformPrices("0.50");
        Map<LocalDate, ElectricityCostCalculator.DayCost> result = ElectricityCostCalculator.calculate(
                DAY_START, DAY_START.plusHours(2), false,
                List.of(event("on", DAY_START), event("off", DAY_START.plusHours(2))),
                new BigDecimal("2"), prices, flatTypes());

        ElectricityCostCalculator.DayCost day = result.get(DAY);
        assertNotNull(day);
        assertDecimal("4.000", day.energy());
        assertDecimal("2.00", day.cost());
        assertEquals(120, Math.round(day.millis() / 60000.0));
    }

    @Test
    @DisplayName("跨小时、跨午夜时按小时电价拆分到不同日期")
    void splitsAcrossHoursAndDates() {
        List<BigDecimal> prices = uniformPrices("0.50");
        prices.set(23, new BigDecimal("0.30"));
        prices.set(0, new BigDecimal("0.30"));
        prices.set(1, new BigDecimal("0.70"));

        List<String> types = flatTypes();
        types.set(23, "valley");
        types.set(0, "valley");
        types.set(1, "peak");

        LocalDateTime start = LocalDate.of(2026, 11, 11).atTime(22, 0);
        LocalDateTime on = LocalDate.of(2026, 11, 11).atTime(23, 0);
        LocalDateTime off = LocalDate.of(2026, 11, 12).atTime(2, 0);
        Map<LocalDate, ElectricityCostCalculator.DayCost> result = ElectricityCostCalculator.calculate(
                start, off, false, List.of(event("on", on), event("off", off)),
                new BigDecimal("1.5"), prices, types);

        ElectricityCostCalculator.DayCost first = result.get(LocalDate.of(2026, 11, 11));
        assertNotNull(first);
        assertDecimal("1.500", first.energy());
        assertDecimal("0.45", first.cost());
        assertDecimal("0.45", first.valleyCost());

        ElectricityCostCalculator.DayCost second = result.get(DAY);
        assertNotNull(second);
        assertDecimal("3.000", second.energy());
        assertDecimal("1.50", second.cost());
        assertDecimal("0.45", second.valleyCost());
        assertDecimal("1.05", second.peakCost());
    }

    @Test
    @DisplayName("统计开始前已开机时按起始时刻补算")
    void countsOngoingIntervalFromRangeStart() {
        Map<LocalDate, ElectricityCostCalculator.DayCost> result = ElectricityCostCalculator.calculate(
                DAY_START, DAY_START.plusHours(3), true,
                List.of(event("off", DAY_START.plusMinutes(90))),
                new BigDecimal("2"), uniformPrices("0.50"), flatTypes());

        ElectricityCostCalculator.DayCost day = result.get(DAY);
        assertNotNull(day);
        assertDecimal("3.000", day.energy());
        assertDecimal("1.50", day.cost());
        assertEquals(90, Math.round(day.millis() / 60000.0));
    }

    @Test
    @DisplayName("当前仍在开机时计算到统计终点")
    void countsOpenEndedIntervalUntilRangeEnd() {
        Map<LocalDate, ElectricityCostCalculator.DayCost> result = ElectricityCostCalculator.calculate(
                DAY_START, DAY_START.plusMinutes(30), false,
                List.of(event("on", DAY_START)),
                new BigDecimal("2"), uniformPrices("0.50"), flatTypes());

        ElectricityCostCalculator.DayCost day = result.get(DAY);
        assertNotNull(day);
        assertDecimal("1.000", day.energy());
        assertDecimal("0.50", day.cost());
        assertEquals(30, Math.round(day.millis() / 60000.0));
    }

    @Test
    @DisplayName("跨午夜且区间内无事件时按两天分别计价")
    void splitsRunningStateAcrossMidnight() {
        List<BigDecimal> prices = uniformPrices("1.00");
        prices.set(23, new BigDecimal("1.00"));
        prices.set(0, new BigDecimal("2.00"));

        Map<LocalDate, ElectricityCostCalculator.DayCost> result = ElectricityCostCalculator.calculate(
                LocalDate.of(2026, 11, 11).atTime(23, 0), DAY_START.plusHours(1), true,
                Collections.emptyList(), BigDecimal.ONE, prices, flatTypes());

        assertDecimal("1.00", result.get(LocalDate.of(2026, 11, 11)).cost());
        assertDecimal("2.00", result.get(DAY).cost());
    }

    @Test
    @DisplayName("无事件且未开机时不产生电费")
    void noCostWhenAeratorNeverRuns() {
        Map<LocalDate, ElectricityCostCalculator.DayCost> result = ElectricityCostCalculator.calculate(
                DAY_START, DAY_START.plusHours(5), false,
                Collections.emptyList(), BigDecimal.ONE, uniformPrices("1.00"), flatTypes());
        assertTrue(result.isEmpty());
    }

    private static AeratorLog event(String action, LocalDateTime time) {
        AeratorLog log = new AeratorLog();
        log.setAction(action);
        log.setPondTime(time);
        return log;
    }

    private static List<BigDecimal> uniformPrices(String price) {
        return new ArrayList<>(Collections.nCopies(24, new BigDecimal(price)));
    }

    private static List<String> flatTypes() {
        return new ArrayList<>(Collections.nCopies(24, "flat"));
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }
}

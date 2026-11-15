package com.example.fished.service.impl;

import com.example.fished.entity.DoSample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryAggregatorTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 0, 0);

    @Test
    @DisplayName("24 小时范围按 5 分钟聚合，统计均值、极值与运行时长")
    void aggregatesSummaryWithinDay() {
        List<DoSample> samples = new ArrayList<>();
        for (int i = 0; i <= 6; i++) {
            samples.add(sample(START.plusMinutes(i * 10L), 5.0 + i, 20.0 + i, i >= 2 && i <= 4));
        }

        HistoryAggregator.Result result = HistoryAggregator.aggregate(
                samples, START, START.plusHours(2), 4.0, 6.0, 2.5);

        assertEquals(1, result.bucketMinutes());
        assertEquals(7, result.sampleCount());
        assertEquals(7, result.points().size());
        assertEquals(8.0, result.summary().avgDo(), 0.001);
        assertEquals(5.0, result.summary().minDo(), 0.001);
        assertEquals(11.0, result.summary().maxDo(), 0.001);
        assertEquals(23.0, result.summary().avgTemperature(), 0.001);
        assertEquals(7.5, result.summary().avgPh(), 0.001);
        assertEquals(7.5, result.summary().minPh(), 0.001);
        assertEquals(7.5, result.summary().maxPh(), 0.001);
        assertEquals(7.5, result.points().get(0).ph(), 0.001);
        assertEquals(30, result.summary().aeratorMinutes());
        assertTrue(result.points().stream().anyMatch(HistoryAggregator.Point::aeratorOn));
    }

    @Test
    @DisplayName("同一时间桶内多条采样取平均")
    void averagesSamplesInSameBucket() {
        List<DoSample> samples = List.of(
                sample(START, 4.0, 20.0, false),
                sample(START.plusMinutes(1), 6.0, 22.0, true),
                sample(START.plusMinutes(2), 5.0, 21.0, false));

        HistoryAggregator.Result result = HistoryAggregator.aggregate(
                samples, START, START.plusHours(24), 4.0, 6.0, 2.5);

        assertEquals(5, result.bucketMinutes());
        assertEquals(1, result.points().size());
        assertEquals(5.0, result.points().get(0).doValue(), 0.001);
        assertEquals(21.0, result.points().get(0).temperature(), 0.001);
        assertTrue(result.points().get(0).aeratorOn());
    }

    @Test
    @DisplayName("低溶氧时长按采样间隔估算并限制在查询范围内")
    void estimatesLowDoMinutes() {
        List<DoSample> samples = List.of(
                sample(START, 3.0, 20.0, false),
                sample(START.plusMinutes(10), 3.5, 20.0, false),
                sample(START.plusMinutes(20), 5.0, 20.0, false),
                sample(START.plusMinutes(30), 5.0, 20.0, false));

        HistoryAggregator.Result result = HistoryAggregator.aggregate(
                samples, START, START.plusMinutes(40), 4.0, 6.0, 2.5);

        assertEquals(20, result.summary().lowDoMinutes());
        assertEquals(0, result.summary().aeratorMinutes());
    }

    @Test
    @DisplayName("30 天范围自动降到 120 分钟一个点")
    void choosesCoarseBucketForMonth() {
        List<DoSample> samples = new ArrayList<>();
        for (int day = 0; day < 30; day++) {
            samples.add(sample(START.plusDays(day), 5.0 + day % 3, 20.0 + day % 5, day % 2 == 0));
        }

        HistoryAggregator.Result result = HistoryAggregator.aggregate(
                samples, START, START.plusDays(30), 4.0, 6.0, 2.5);

        assertEquals(120, result.bucketMinutes());
        assertEquals(30, result.points().size());
        assertTrue(result.points().get(0).time().isBefore(result.points().get(29).time()));
    }

    @Test
    @DisplayName("没有采样时返回空序列和零统计")
    void handlesEmptySamples() {
        HistoryAggregator.Result result = HistoryAggregator.aggregate(
                List.of(), START, START.plusHours(24), 4.0, 6.0, 2.5);

        assertEquals(0, result.sampleCount());
        assertTrue(result.points().isEmpty());
        assertEquals(0, result.summary().avgDo(), 0.001);
        assertEquals(0, result.summary().avgTemperature(), 0.001);
        assertEquals(0, result.summary().aeratorMinutes());
        assertEquals(4.0, result.summary().onThreshold(), 0.001);
    }

    @Test
    @DisplayName("老数据没有 pH 时按 0 处理，不影响溶氧统计")
    void handlesMissingPh() {
        DoSample legacy = sample(START, 5.0, 20.0, false);
        legacy.setPhValue(null);

        HistoryAggregator.Result result = HistoryAggregator.aggregate(
                List.of(legacy), START, START.plusHours(24), 4.0, 6.0, 2.5);

        assertEquals(0, result.summary().avgPh(), 0.001);
        assertEquals(5.0, result.summary().avgDo(), 0.001);
        assertEquals(0, result.points().get(0).ph(), 0.001);
    }

    @Test
    @DisplayName("点时间被限制在查询起止范围内")
    void clampsPointTimeToRange() {
        List<DoSample> samples = List.of(
                sample(START.minusMinutes(1), 5.0, 20.0, false),
                sample(START.plusMinutes(74), 5.0, 20.0, false));

        HistoryAggregator.Result result = HistoryAggregator.aggregate(
                samples, START, START.plusHours(2), 4.0, 6.0, 2.5);

        assertTrue(result.points().stream().allMatch(p ->
                !p.time().isBefore(START) && !p.time().isAfter(START.plusHours(2))));
    }

    private static DoSample sample(LocalDateTime time, double doValue, double temperature, boolean on) {
        DoSample sample = new DoSample();
        sample.setPondId(1L);
        sample.setPondTime(time);
        sample.setDoValue(BigDecimal.valueOf(doValue));
        sample.setTemperature(BigDecimal.valueOf(temperature));
        sample.setPhValue(BigDecimal.valueOf(7.5));
        sample.setAeratorOn(on);
        return sample;
    }
}

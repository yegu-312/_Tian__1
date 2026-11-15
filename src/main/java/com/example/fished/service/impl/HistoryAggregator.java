package com.example.fished.service.impl;

import com.example.fished.entity.DoSample;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 历史采样聚合：统计区间指标，并把原始采样降采样到约 500 个点。
 * 纯函数实现，便于单元测试。
 */
final class HistoryAggregator {

    /** 目标绘图点数，避免一个月 4 万多条原始数据直接压给 Canvas。 */
    private static final int TARGET_POINTS = 480;
    private static final int[] BUCKET_STEPS = {1, 2, 5, 10, 15, 30, 60, 120, 180, 240, 360, 720, 1440};

    private HistoryAggregator() {
    }

    static Result aggregate(List<DoSample> rows, LocalDateTime from, LocalDateTime to,
                            double onThreshold, double offThreshold, double safetyFloor) {
        List<DoSample> samples = rows == null ? List.of() : rows;
        long spanMinutes = Math.max(1, Duration.between(from, to).toMinutes());
        int bucketMinutes = chooseBucket(spanMinutes);

        double sumDo = 0;
        double sumTemp = 0;
        double sumPh = 0;
        int countDo = 0;
        int countTemp = 0;
        int countPh = 0;
        double minDo = Double.POSITIVE_INFINITY;
        double maxDo = Double.NEGATIVE_INFINITY;
        double minTemp = Double.POSITIVE_INFINITY;
        double maxTemp = Double.NEGATIVE_INFINITY;
        double minPh = Double.POSITIVE_INFINITY;
        double maxPh = Double.NEGATIVE_INFINITY;
        int onCount = 0;
        int lowCount = 0;
        List<Long> deltas = new ArrayList<>();

        for (int i = 0; i < samples.size(); i++) {
            DoSample sample = samples.get(i);
            Double doValue = toDouble(sample.getDoValue());
            Double temperature = toDouble(sample.getTemperature());
            Double ph = toDouble(sample.getPhValue());

            if (doValue != null) {
                sumDo += doValue;
                countDo++;
                minDo = Math.min(minDo, doValue);
                maxDo = Math.max(maxDo, doValue);
                if (doValue < onThreshold) {
                    lowCount++;
                }
            }
            if (temperature != null) {
                sumTemp += temperature;
                countTemp++;
                minTemp = Math.min(minTemp, temperature);
                maxTemp = Math.max(maxTemp, temperature);
            }
            if (ph != null) {
                sumPh += ph;
                countPh++;
                minPh = Math.min(minPh, ph);
                maxPh = Math.max(maxPh, ph);
            }
            if (Boolean.TRUE.equals(sample.getAeratorOn())) {
                onCount++;
            }
            if (i > 0 && samples.get(i - 1).getPondTime() != null && sample.getPondTime() != null) {
                long delta = Duration.between(samples.get(i - 1).getPondTime(), sample.getPondTime()).toMinutes();
                if (delta > 0 && delta <= 1440) {
                    deltas.add(delta);
                }
            }
        }

        long sampleIntervalMinutes = median(deltas);
        Summary summary = new Summary(
                countDo == 0 ? 0 : round2(sumDo / countDo),
                countDo == 0 ? 0 : round2(minDo),
                countDo == 0 ? 0 : round2(maxDo),
                countTemp == 0 ? 0 : round2(sumTemp / countTemp),
                countTemp == 0 ? 0 : round2(minTemp),
                countTemp == 0 ? 0 : round2(maxTemp),
                Math.min(spanMinutes, onCount * sampleIntervalMinutes),
                Math.min(spanMinutes, lowCount * sampleIntervalMinutes),
                onThreshold, offThreshold, safetyFloor,
                countPh == 0 ? 0 : round2(sumPh / countPh),
                countPh == 0 ? 0 : round2(minPh),
                countPh == 0 ? 0 : round2(maxPh));

        Map<Long, Bucket> buckets = new TreeMap<>();
        for (DoSample sample : samples) {
            if (sample.getPondTime() == null) {
                continue;
            }
            long epochMinute = Math.floorDiv(sample.getPondTime().toEpochSecond(ZoneOffset.UTC), 60);
            long key = Math.floorDiv(epochMinute, bucketMinutes);
            buckets.computeIfAbsent(key, ignored -> new Bucket()).add(sample);
        }

        List<Point> points = new ArrayList<>(buckets.size());
        for (Map.Entry<Long, Bucket> entry : buckets.entrySet()) {
            Bucket bucket = entry.getValue();
            if (bucket.countDo == 0) {
                continue;
            }
            LocalDateTime time = LocalDateTime.ofEpochSecond(
                    entry.getKey() * bucketMinutes * 60L, 0, ZoneOffset.UTC);
            if (time.isBefore(from)) {
                time = from;
            } else if (time.isAfter(to)) {
                time = to;
            }
            points.add(new Point(time, round2(bucket.sumDo / bucket.countDo),
                    bucket.countTemp == 0 ? 0 : round2(bucket.sumTemp / bucket.countTemp),
                    bucket.anyOn,
                    bucket.countPh == 0 ? 0 : round2(bucket.sumPh / bucket.countPh)));
        }

        return new Result(bucketMinutes, samples.size(), summary, points);
    }

    private static int chooseBucket(long spanMinutes) {
        double target = spanMinutes / (double) TARGET_POINTS;
        for (int step : BUCKET_STEPS) {
            if (step >= target) {
                return step;
            }
        }
        return BUCKET_STEPS[BUCKET_STEPS.length - 1];
    }

    private static long median(List<Long> values) {
        if (values.isEmpty()) {
            return 1;
        }
        Collections.sort(values);
        long value = values.get(values.size() / 2);
        return Math.max(1, Math.min(1440, value));
    }

    private static Double toDouble(java.math.BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class Bucket {
        private double sumDo;
        private double sumTemp;
        private double sumPh;
        private int countDo;
        private int countTemp;
        private int countPh;
        private boolean anyOn;

        private void add(DoSample sample) {
            if (sample.getDoValue() != null) {
                sumDo += sample.getDoValue().doubleValue();
                countDo++;
            }
            if (sample.getTemperature() != null) {
                sumTemp += sample.getTemperature().doubleValue();
                countTemp++;
            }
            if (sample.getPhValue() != null) {
                sumPh += sample.getPhValue().doubleValue();
                countPh++;
            }
            if (Boolean.TRUE.equals(sample.getAeratorOn())) {
                anyOn = true;
            }
        }
    }

    record Result(int bucketMinutes, int sampleCount, Summary summary, List<Point> points) {
    }

    record Summary(double avgDo, double minDo, double maxDo,
                   double avgTemperature, double minTemperature, double maxTemperature,
                   long aeratorMinutes, long lowDoMinutes,
                   double onThreshold, double offThreshold, double safetyFloor,
                   double avgPh, double minPh, double maxPh) {
    }

    record Point(LocalDateTime time, double doValue, double temperature,
                 boolean aeratorOn, double ph) {
    }
}

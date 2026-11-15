package com.example.fished.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 鱼塘历史记录：按时间桶降采样后的溶氧 / 水温 / 增氧机序列与区间统计。
 */
@Data
public class HistorySeriesVO {

    private Long pondId;
    private String pondName;
    private LocalDateTime from;
    private LocalDateTime to;
    private LocalDateTime currentPondTime;
    private int retentionDays;
    private int bucketMinutes;
    private int sampleCount;
    private Summary summary;
    private List<Point> points;

    /** 单个绘图点。 */
    public record Point(LocalDateTime time, double doValue, double temperature,
                        boolean aeratorOn, double ph) {
        /** 兼容旧调用：没有 pH 数据时记为 0。 */
        public Point(LocalDateTime time, double doValue, double temperature, boolean aeratorOn) {
            this(time, doValue, temperature, aeratorOn, 0);
        }
    }

    /** 区间统计。 */
    public record Summary(double avgDo, double minDo, double maxDo,
                          double avgTemperature, double minTemperature, double maxTemperature,
                          long aeratorMinutes, long lowDoMinutes,
                          double onThreshold, double offThreshold, double safetyFloor,
                          double avgPh, double minPh, double maxPh) {
        /** 兼容旧调用：没有 pH 数据时记为 0。 */
        public Summary(double avgDo, double minDo, double maxDo,
                       double avgTemperature, double minTemperature, double maxTemperature,
                       long aeratorMinutes, long lowDoMinutes,
                       double onThreshold, double offThreshold, double safetyFloor) {
            this(avgDo, minDo, maxDo, avgTemperature, minTemperature, maxTemperature,
                    aeratorMinutes, lowDoMinutes, onThreshold, offThreshold, safetyFloor,
                    0, 0, 0);
        }
    }
}

package com.example.fished.service.impl;

import com.example.fished.common.enums.AeratorActionEnum;
import com.example.fished.entity.AeratorLog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯计算逻辑：把增氧机 on/off 事件还原成开机区间，按自然小时拆分，
 * 再乘上额定功率与对应小时的电价。与数据库、Spring 完全解耦，便于单元测试。
 */
final class ElectricityCostCalculator {

    private ElectricityCostCalculator() {
    }

    /**
     * @param rangeStart     统计起点（含）
     * @param rangeEnd       统计终点（含当前时刻）
     * @param runningAtStart 统计起点之前增氧机是否已经处于开机状态
     * @param events         区间内按 pondTime、id 升序排列的开关事件
     * @param powerKw        增氧机额定功率
     * @param hourlyPrices   长度 24 的每小时电价
     * @param hourlyTypes    长度 24 的每小时时段类型（peak / flat / valley）
     * @return 按鱼塘日期聚合的电量、电费与运行时长
     */
    static Map<LocalDate, DayCost> calculate(LocalDateTime rangeStart,
                                             LocalDateTime rangeEnd,
                                             boolean runningAtStart,
                                             List<AeratorLog> events,
                                             BigDecimal powerKw,
                                             List<BigDecimal> hourlyPrices,
                                             List<String> hourlyTypes) {
        Map<LocalDate, DayCost> days = new HashMap<>();
        if (rangeStart == null || rangeEnd == null || !rangeStart.isBefore(rangeEnd)
                || powerKw == null || powerKw.signum() <= 0) {
            return days;
        }

        LocalDateTime cursor = rangeStart;
        boolean running = runningAtStart;
        if (events != null) {
            for (AeratorLog event : events) {
                LocalDateTime time = event == null ? null : event.getPondTime();
                if (time == null || time.isBefore(rangeStart)) {
                    continue;
                }
                if (!time.isBefore(rangeEnd)) {
                    break;
                }
                if (running) {
                    addInterval(days, cursor, time, powerKw, hourlyPrices, hourlyTypes);
                }
                running = AeratorActionEnum.ON.getCode().equals(event.getAction());
                cursor = time;
            }
        }
        if (running && cursor.isBefore(rangeEnd)) {
            addInterval(days, cursor, rangeEnd, powerKw, hourlyPrices, hourlyTypes);
        }
        return days;
    }

    /** 把 [from, to) 按整点切开，避免跨电价时段的区间被错误计价。 */
    private static void addInterval(Map<LocalDate, DayCost> days,
                                    LocalDateTime from,
                                    LocalDateTime to,
                                    BigDecimal powerKw,
                                    List<BigDecimal> hourlyPrices,
                                    List<String> hourlyTypes) {
        LocalDateTime cursor = from;
        while (cursor.isBefore(to)) {
            LocalDateTime nextHour = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            LocalDateTime segmentEnd = nextHour.isBefore(to) ? nextHour : to;
            long millis = Duration.between(cursor, segmentEnd).toMillis();
            if (millis > 0) {
                BigDecimal hours = BigDecimal.valueOf(millis)
                        .divide(BigDecimal.valueOf(3_600_000L), 10, RoundingMode.HALF_UP);
                BigDecimal energy = powerKw.multiply(hours);
                int hour = cursor.getHour();
                BigDecimal price = hour < hourlyPrices.size() && hourlyPrices.get(hour) != null
                        ? hourlyPrices.get(hour) : BigDecimal.ZERO;
                String type = hour < hourlyTypes.size() && hourlyTypes.get(hour) != null
                        ? hourlyTypes.get(hour) : "flat";
                days.computeIfAbsent(cursor.toLocalDate(), ignored -> new DayCost())
                        .add(energy, energy.multiply(price), type, millis);
            }
            cursor = segmentEnd;
        }
    }

    /** 便于测试与汇总的可变累加器，只在计算过程中使用。 */
    static final class DayCost {
        private BigDecimal energy = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;
        private BigDecimal peakCost = BigDecimal.ZERO;
        private BigDecimal flatCost = BigDecimal.ZERO;
        private BigDecimal valleyCost = BigDecimal.ZERO;
        private long millis;

        private void add(BigDecimal energy, BigDecimal cost, String type, long millis) {
            this.energy = this.energy.add(energy);
            this.cost = this.cost.add(cost);
            switch (type) {
                case "peak" -> this.peakCost = this.peakCost.add(cost);
                case "valley" -> this.valleyCost = this.valleyCost.add(cost);
                default -> this.flatCost = this.flatCost.add(cost);
            }
            this.millis += millis;
        }

        BigDecimal energy() { return energy; }
        BigDecimal cost() { return cost; }
        BigDecimal peakCost() { return peakCost; }
        BigDecimal flatCost() { return flatCost; }
        BigDecimal valleyCost() { return valleyCost; }
        long millis() { return millis; }
    }
}

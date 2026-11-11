package com.example.fished.sim;

import lombok.Getter;

/**
 * 水温辅助检测规则。
 *
 * <p>溶氧仍是主指标；水温只用于展示和异常提示，不参与自动增氧决策。
 * 18~32℃ 作为常见养殖水体的适宜区间，越界后产生辅助告警。</p>
 */
public final class TemperatureGuard {

    public static final double COMFORT_MIN = 18.0;
    public static final double COMFORT_MAX = 32.0;
    /** 回差：越界后要回到区间内 0.5℃，才认为恢复，避免临界值反复告警。 */
    public static final double HYSTERESIS = 0.5;

    private TemperatureGuard() {
    }

    @Getter
    public enum Status {
        NORMAL("normal", "适宜"),
        HIGH("high", "偏高"),
        LOW("low", "偏低");

        private final String code;
        private final String desc;

        Status(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }

    public static Status status(double temperature) {
        if (Double.isNaN(temperature)) {
            return Status.NORMAL;
        }
        if (temperature > COMFORT_MAX) {
            return Status.HIGH;
        }
        if (temperature < COMFORT_MIN) {
            return Status.LOW;
        }
        return Status.NORMAL;
    }

    public static boolean isHigh(double temperature) {
        return temperature > COMFORT_MAX;
    }

    public static boolean isLow(double temperature) {
        return temperature < COMFORT_MIN;
    }

    public static boolean highRecovered(double temperature) {
        return temperature <= COMFORT_MAX - HYSTERESIS;
    }

    public static boolean lowRecovered(double temperature) {
        return temperature >= COMFORT_MIN + HYSTERESIS;
    }
}

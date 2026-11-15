package com.example.fished.sim;

/**
 * pH 辅助判断：只用于展示和告警提示，不参与增氧机自动决策。
 * 6.5-8.5 为常见养殖水体的适宜区间。
 */
public final class PhGuard {

    public static final double COMFORT_MIN = 6.5;
    public static final double COMFORT_MAX = 8.5;

    private PhGuard() {
    }

    /** 返回 low / optimal / high。 */
    public static String code(double ph) {
        if (ph < COMFORT_MIN) {
            return "low";
        }
        if (ph > COMFORT_MAX) {
            return "high";
        }
        return "optimal";
    }

    public static String status(double ph) {
        return switch (code(ph)) {
            case "low" -> "偏酸";
            case "high" -> "偏碱";
            default -> "适宜";
        };
    }
}

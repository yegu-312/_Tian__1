package com.example.fished.vo;

import com.example.fished.sim.PhGuard;

/**
 * pH 辅助状态。pH 只用于展示和提示，不参与自动增氧决策。
 */
public record PhStatusVO(double value, String statusCode, String statusDesc,
                         double comfortMin, double comfortMax) {

    public static PhStatusVO from(double ph) {
        return new PhStatusVO(ph, PhGuard.code(ph), PhGuard.status(ph),
                PhGuard.COMFORT_MIN, PhGuard.COMFORT_MAX);
    }
}

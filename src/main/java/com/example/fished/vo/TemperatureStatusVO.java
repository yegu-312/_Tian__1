package com.example.fished.vo;

import com.example.fished.sim.TemperatureGuard;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 水温辅助状态：适宜区间由后端统一提供，前端不再硬编码阈值。
 */
@Data
@AllArgsConstructor
public class TemperatureStatusVO {

    private double comfortMin;
    private double comfortMax;
    private String status;
    private String statusDesc;

    public static TemperatureStatusVO from(double temperature) {
        TemperatureGuard.Status status = TemperatureGuard.status(temperature);
        return new TemperatureStatusVO(
                TemperatureGuard.COMFORT_MIN,
                TemperatureGuard.COMFORT_MAX,
                status.getCode(),
                status.getDesc());
    }
}

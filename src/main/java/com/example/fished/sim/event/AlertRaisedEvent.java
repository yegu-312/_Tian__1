package com.example.fished.sim.event;

import com.example.fished.common.enums.AlertLevelEnum;
import com.example.fished.common.enums.AlertTypeEnum;
import com.example.fished.sim.TemperatureGuard;

import java.time.LocalDateTime;

public record AlertRaisedEvent(
        long pondId,
        LocalDateTime pondTime, AlertTypeEnum alertType,
        AlertLevelEnum level,String message,Double doValue
        ) implements SimEvent{
    public static AlertRaisedEvent safetyFloor(long pondId, LocalDateTime pondTime,
                                               double doValue, double safetyFloor){
        return new AlertRaisedEvent(
                pondId,
                pondTime,AlertTypeEnum.DO_SAFETY_FLOOR, AlertLevelEnum.URGENT,
                String.format("溶氧 %.2f 跌破安全下限%.2f", doValue, safetyFloor),doValue);
    }
    public static AlertRaisedEvent onThreshold(long pondId, LocalDateTime pondTime,
                                               double doValue, double onThreshold){
       return new AlertRaisedEvent(
               pondId,
               pondTime,AlertTypeEnum.DO_ON_THRESHOLD,AlertLevelEnum.WARN,
               String.format("溶氧 %.2f 低于开机阈值%.2f", doValue, onThreshold),doValue);
    }

    /** 水温辅助告警：不携带溶氧值，消息中记录水温。 */
    public static AlertRaisedEvent temperatureHigh(long pondId, LocalDateTime pondTime,
                                                   double temperature){
        return new AlertRaisedEvent(
                pondId,
                pondTime,AlertTypeEnum.TEMPERATURE_HIGH,AlertLevelEnum.WARN,
                String.format("水温 %.1f℃ 高于适宜上限 %.1f℃", temperature,
                        TemperatureGuard.COMFORT_MAX), null);
    }

    public static AlertRaisedEvent temperatureLow(long pondId, LocalDateTime pondTime,
                                                  double temperature){
        return new AlertRaisedEvent(
                pondId,
                pondTime,AlertTypeEnum.TEMPERATURE_LOW,AlertLevelEnum.WARN,
                String.format("水温 %.1f℃ 低于适宜下限 %.1f℃", temperature,
                        TemperatureGuard.COMFORT_MIN), null);
    }
}

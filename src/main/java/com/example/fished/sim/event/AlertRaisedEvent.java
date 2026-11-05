package com.example.fished.sim.event;

import com.example.fished.common.enums.AlertLevelEnum;
import com.example.fished.common.enums.AlertTypeEnum;

import java.time.LocalDateTime;

public record AlertRaisedEvent(
        long pondId,
        LocalDateTime pondTime, AlertTypeEnum alertType,
        AlertLevelEnum level,String message,double doValue
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
}

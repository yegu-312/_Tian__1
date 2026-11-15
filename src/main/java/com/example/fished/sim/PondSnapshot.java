package com.example.fished.sim;

import java.time.LocalDateTime;

public record PondSnapshot (
    LocalDateTime pondTime,
    double currentDo,//当前溶氧
    double temperature,
    boolean aeratorOn,
    boolean paused,
    int speed,
    long elapsedMinutes,//以运行多久
    double ph//当前 pH，辅助水质指标，不参与增氧决策
){
    /** 兼容旧调用：未显式提供 pH 时使用默认值。 */
    public PondSnapshot(LocalDateTime pondTime, double currentDo, double temperature,
                        boolean aeratorOn, boolean paused, int speed, long elapsedMinutes) {
        this(pondTime, currentDo, temperature, aeratorOn, paused, speed,
                elapsedMinutes, Physics.DEFAULT_PH);
    }

    public static PondSnapshot initial(LocalDateTime pondTime,
                                       double currentDo,double temperature){
        return new PondSnapshot(pondTime,currentDo,temperature,false,
                false,1,0L,Physics.DEFAULT_PH);
    }
}

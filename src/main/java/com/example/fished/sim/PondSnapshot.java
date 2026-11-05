package com.example.fished.sim;

import java.time.LocalDateTime;

public record PondSnapshot (
    LocalDateTime pondTime,
    double currentDo,//当前溶氧
    double temperature,
    boolean aeratorOn,
    boolean paused,
    int speed,
    long elapsedMinutes//以运行多久
){
    public static PondSnapshot initial(LocalDateTime pondTime,
                                       double currentDo,double temperature){
        return new PondSnapshot(pondTime,currentDo,temperature,false,
                false,1,0L);
    }
}

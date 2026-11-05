package com.example.fished.sim.event;


import java.time.LocalDateTime;

public record AeratorStateChangedEvent(
        long pondId,
        LocalDateTime pondTime,
        boolean on,String reason,double doValue,String operator
)implements SimEvent {
    public static AeratorStateChangedEvent auto(long pondId, LocalDateTime pondTime,
                                                boolean on, String reason,double doValue){
        return new AeratorStateChangedEvent(pondId,pondTime,on,reason,doValue,"system");
    }
}

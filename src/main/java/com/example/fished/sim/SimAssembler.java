package com.example.fished.sim;

import com.example.fished.entity.AeratorStrategy;
import com.example.fished.entity.PondConfig;
import com.example.fished.entity.SimSetting;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class SimAssembler {
    private SimAssembler() {

    }
    public static SimParams toSimParams(PondConfig c){
        if(c==null){
            return SimParams.defaults();
        }
        return new SimParams(
                nvl(c.getTemperatureMode(),"diurnal"),
                d(c.getBaseTemperature(),25.0),
                d(c.getDailyAmplitude(),2.0),
                d(c.getDoMin(), 3.0),
                d(c.getDoMax(), 12.0),
                d(c.getProduceCoefficient(), 0.6),
                d(c.getConsumeCoefficient(), 0.09),
                d(c.getAeratorRate(), 1.2));
    }
    public static StrategyParams toStrategyParams(AeratorStrategy s, SimSetting setting){
     if(s==null){
         return StrategyParams.defaults();
     }
     return new StrategyParams(
             nvl(s.getMode(), "auto"),
             d(s.getOnThreshold(), 4.0),
             d(s.getOffThreshold(), 6.0),
             d(s.getSafetyFloor(), 2.5),
             i(s.getPredictMinutes(), 0),
             b(s.getManualOn(), false),
             b(setting == null ? null :
                     setting.getSafetyVetoEnabled(), true));
    }
    private static double  d(BigDecimal v, double fallback){
        return v==null?fallback:v.doubleValue();
    }
    private static int i(Integer v,int fallback){
        return v==null?fallback:v;
    }
    private static boolean b(Boolean v,boolean fallback){
        return v==null?fallback:v;
    }
    private static  String nvl(String v,String fallback){
        return (v==null||v.isBlank())?fallback:v;
    }
    public static LocalDateTime absoluteTime(PondSnapshot anchor,DoPoint point){
        long minutesAgo = anchor.elapsedMinutes() - point.minuteOffset();
        return anchor.pondTime().minusMinutes(minutesAgo);
    }
}

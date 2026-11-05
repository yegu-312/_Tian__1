package com.example.fished.sim;

import com.example.fished.common.enums.TemperatureModeEnum;

import java.time.LocalDateTime;

//玩水公式模型
public final class Physics {

    private static final double SUNRISE_HOUR=6.0;
    private static final double SUNSET_HOUR=18.0;
    private static final double HOTTEST_HOUR=15.0;
    private static final double Q10=2.0;
    private static final double REFERENCE_TEMPERATURE=20.0;
    private static final double ESCAPE_RATE=0.5;

    private Physics(){}

    public static double saturationDo(double temperature){
        return 14.652
                - 0.41022*temperature
                + 0.007991*temperature*temperature
                - 0.000077774*temperature*temperature*temperature;
    }

    public static double waterTemperature(LocalDateTime pondTime,SimParams params){
        if(TemperatureModeEnum.FIXED.getCode().equals(params.temperatureMode())){
            return params.baseTemperature();
        }
        double hour=hourOf(pondTime);
        return params.baseTemperature()+
                params.dailyAmplitude()*Math.cos(2*Math.PI*(hour-HOTTEST_HOUR)/24.0);
    }
    public static double daylightFactor(LocalDateTime pondTime) {
        double hour=hourOf(pondTime);
        if(hour<SUNRISE_HOUR||hour>SUNSET_HOUR){
            return 0.0;
        }
        return Math.sin(Math.PI*(hour-SUNRISE_HOUR)/(SUNSET_HOUR-SUNRISE_HOUR));
    }
    public static double nextDo(double doValue,
                                double waterTemperature,
                                double daylightFactor,
                                boolean aeratorOn,
                                SimParams params,
                                double dtHours,
                                double noise){
        double saturation=saturationDo(waterTemperature);
        double deficit=Math.max(0.0,1.0-doValue/saturation);
        double produce=params.produceCoefficient()*daylightFactor*deficit;
        double temperaturFactor=Math.pow(Q10,(waterTemperature-REFERENCE_TEMPERATURE)/10.0);
        double consume=params.consumeCoefficient()*temperaturFactor;
        double aeration=aeratorOn? params.aeratorRate()*deficit:0.0;
        double escape=ESCAPE_RATE*Math.max(0.0,doValue-saturation);
        double next=doValue+(produce+aeration-consume-escape)*dtHours+noise;
        return Math.max(0.0,next);
    }
    private static double hourOf(LocalDateTime time){
        return time.getHour()
                + time.getMinute()/60.0
                + time.getSecond()/3600.0;
    }
}

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

    /** pH 辅助模型：默认中性偏碱，换水/调水会改变它，之后缓慢回归自然值。 */
    public static final double DEFAULT_PH = 7.50;
    public static final double SOURCE_WATER_PH = 7.20;
    public static final double PH_MIN = 5.50;
    public static final double PH_MAX = 9.50;
    private static final double PH_RELAX_PER_HOUR = 0.12;

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

    /** 自然状态下的 pH，白天光合作用会略微抬高，夜间回落到 7.5 附近。 */
    public static double naturalPh(LocalDateTime time) {
        return DEFAULT_PH + 0.12 * daylightFactor(time);
    }

    /** pH 向自然值缓慢回归；换水/调水造成的偏移不会立刻消失。 */
    public static double nextPh(double current, double natural, double dtHours, double noise) {
        double rate = Math.max(0, Math.min(0.5, PH_RELAX_PER_HOUR * dtHours));
        double next = current + (natural - current) * rate + noise;
        return clampPh(next);
    }

    /** 换水：按比例把当前 pH 稀释到水源 pH。 */
    public static double exchangePh(double current, double ratio) {
        double safeRatio = Math.max(0, Math.min(1, ratio));
        return clampPh(current * (1 - safeRatio) + SOURCE_WATER_PH * safeRatio);
    }

    /** 调水：投放调水剂后，pH 向目标值移动；strength 越大，单次调整幅度越大。 */
    public static double adjustPh(double current, double target, int strength) {
        double rate = Math.max(0.10, Math.min(0.90, 0.25 * Math.max(1, strength)));
        return clampPh(current + (target - current) * rate);
    }

    public static double clampPh(double value) {
        return Math.max(PH_MIN, Math.min(PH_MAX, value));
    }

    private static double hourOf(LocalDateTime time){
        return time.getHour()
                + time.getMinute()/60.0
                + time.getSecond()/3600.0;
    }
}

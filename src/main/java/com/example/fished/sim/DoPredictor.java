package com.example.fished.sim;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
//溶氧预测器，推算未来 N 分钟的溶氧走势
public final class DoPredictor {

    private static final int STEP_MINUTES=1;

    private static final double NO_NOISE=0.0;
    private DoPredictor(){
    }

    public static List<DoPoint> predict(double startDo, LocalDateTime startTime,long startMinuteOffset,
                                        boolean aeratorOn,SimParams params, int minutesAhead){//minutesAhead:往前预测多少分钟
        List<DoPoint> points = new ArrayList<>(Math.max(0, minutesAhead));
        double doValue = startDo;//起始溶氧
        LocalDateTime t = startTime;

        for (int i = 1; i <= minutesAhead; i++) {
            t = t.plusMinutes(STEP_MINUTES);// 时间推进 1 分钟
            double temperature = Physics.waterTemperature(t, params);
            double light = Physics.daylightFactor(t);
            doValue = Physics.nextDo(doValue, temperature, light,
                    aeratorOn, params, STEP_MINUTES /
                            60.0, NO_NOISE);
            points.add(new DoPoint(startMinuteOffset + i, round2(doValue),
                    round2(temperature), aeratorOn));
        }
        return points;
    }
    //预测最低溶氧
    public static double predictMin(double startDo,LocalDateTime startTime,boolean aeratorOn,
                                    SimParams params,int minutesAhead){
        return predict(startDo,startTime,0L,aeratorOn,params,minutesAhead)
                .stream().mapToDouble(DoPoint::doValue).min().orElse(startDo);
    }
    private static double round2(double value){
        return Math.round(value * 100.0) / 100.0;
    }
}

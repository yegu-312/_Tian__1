package com.example.fished.sim;

public record SimParams(
        String temperatureMode,
        double baseTemperature,
        double dailyAmplitude,
        double doMin,
        double doMax,
        double produceCoefficient,
        double consumeCoefficient,
        double aeratorRate
) {
    public static SimParams defaults(){
        return new SimParams("diurnal",25.0,2.0,
                3.0,12.0,0.600,0.090,1.200);
    }
}

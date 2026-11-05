package com.example.fished.sim;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PondState {

    private LocalDateTime pondTime;
    private double doValue;
    private double temperature;
    private boolean aeratorOn;
    private boolean paused;
    private int speed;

    private long elapsedMinutes;
    private boolean safetyAlertActive;
    private boolean thresholdAlertActive;

    public PondState(LocalDateTime pondTime,double doValue, double temperature){
        reset(pondTime,doValue,temperature);
    }

    public void advance(LocalDateTime newPondTime,long advanceMinutes){
        this.pondTime = newPondTime;
        this.elapsedMinutes += advanceMinutes;
    }
    public void reset(LocalDateTime startTime,double initialDo, double initialTemperature){
        this.pondTime = startTime;
        this.doValue = initialDo;
        this.temperature = initialTemperature;
        this.paused = false;
        this.speed = 1;
        this.elapsedMinutes = 0L;
        this.thresholdAlertActive = false;
        this.safetyAlertActive = false;
        this.aeratorOn = false;
    }
}

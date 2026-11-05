package com.example.fished.vo;

import com.example.fished.sim.DoPoint;
import com.example.fished.sim.PondSnapshot;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Data;

import java.util.List;

@Data
public class RealtimeVO {

    private static final String PREDICTION_NOTE="无噪声确定性前推：假设增氧机状态不变、水温与光照按同一条曲线走。仅供趋势参考，不是真预测。";

    @JsonUnwrapped
    private PondSnapshot currentDo;

    private List<DoPoint> history;

    private List<DoPoint> prediction;

    private Thresholds thresholds;//三条阈值线
    // 定义不可变数据类：三个阈值
    public record Thresholds(double safetyFloor,double onThreshold,double offThreshold){

    }
    //免责声明
    public String getPredictionNote(){
        return PREDICTION_NOTE;
    }
}

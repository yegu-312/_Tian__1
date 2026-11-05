package com.example.fished.sim;

import com.example.fished.common.BusinessException;
import com.example.fished.common.enums.AeratorModeEnum;

public record StrategyParams(
        String mode,
        double onThreshold,
        double offThreshold,
        double safetyFloor,
        int predictMinutes,
        boolean manualOn,
        boolean safetyVetoEnabled
) {
    //校验器,判断条件是否满足要求
    public StrategyParams{
        if(AeratorModeEnum.getByCode(mode).isEmpty()){
            throw new BusinessException("未知的增氧机模式："+mode);
        }
        if(offThreshold<=onThreshold){
            throw new BusinessException(
                    "关机阈值（"+offThreshold+
                            ")必须大于开机阈值("+onThreshold+")，否则增氧机会反复开关");
        }
        if(safetyFloor>onThreshold){
            throw new BusinessException(
                    "安全下限("+safetyFloor+")不能高于开机阈值("+onThreshold+")"
            );
        }
        if(predictMinutes<0){
            throw new BusinessException("提前开机分钟数不能为负");
        }
    }
    //校验默认值
    public static StrategyParams defaults(){
        return new StrategyParams("auto",4.0,6.0,
                2.5,0,false,true);
    }
}

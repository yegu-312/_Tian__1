package com.example.fished.sim;

import com.example.fished.common.enums.AeratorModeEnum;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AeratorDecider {
    public Decision decide(PondSnapshot snapshot,
                           StrategyParams params,Double predictedMin){
        if(params.safetyVetoEnabled()&&
        snapshot.currentDo()<params.safetyFloor()){
            return Decision.turnOn(String.format(
                    "安全否决：溶氧 %.2f 低于安全下限 %.2f",snapshot.currentDo(),
                    params.safetyFloor()));
        }
        if(AeratorModeEnum.MANUAL.getCode().equals(params.mode())){
            if(params.manualOn()==snapshot.aeratorOn()){
                return Decision.keep(snapshot.aeratorOn());
            }
            return params.manualOn()?Decision.turnOn("手动模式：人工开启")
                    :Decision.turnOff("手动模式：人工关闭");
        }
        if(!snapshot.aeratorOn()&&params.predictMinutes()>0
        &&predictedMin!=null&&predictedMin<params.onThreshold()){
            return Decision.turnOn(String.format(
                    "预测到未来 %d分钟内将跌破开机阈值（预测最低 %.2f）",
                    params.predictMinutes(),predictedMin
            ));
        }
        if(!snapshot.aeratorOn()&&snapshot.currentDo()<params.onThreshold()){
            return Decision.turnOn(String.format(
                    "溶氧 %.2f 低于开机阈值 %.2f",snapshot.currentDo(),params.onThreshold()));
        }
        if(snapshot.aeratorOn()&&snapshot.currentDo()>params.offThreshold()){
            return Decision.turnOff(String.format(
                    "溶氧 %.2f 高于关机阈值 %.2f",snapshot.currentDo(),params.offThreshold()));
        }
        return Decision.keep(snapshot.aeratorOn());
    }
}

package com.example.fished.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
//设置更新
@Data
public class SettingUpdateDTO {

    @NotNull(message = "安全否决开关不能为空")
    private Boolean safetyVetoEnabled;
    @NotNull(message = "模拟步长不能为空")
    @Min(value = 60, message = "模拟步长至少 60秒（引擎按整分钟累计，小于 60 会被截断成 0）")
    @Max(value = 3600, message = "模拟步长最多 3600秒")
    private Integer stepSeconds;

    @NotNull(message = "采样间隔不能为空")
    @Min(value = 60, message = "采样间隔至少 60 秒")
    @Max(value = 86400, message = "采样间隔最多 86400秒（一天）")
    private Integer sampleIntervalSec;

    @AssertTrue(message="采样间隔必须是60的整数倍")
    public boolean isSampleIntervalAligned(){
        if(sampleIntervalSec==null){
            return true;
        }
        return sampleIntervalSec % 60==0;
    }

    @AssertTrue(message = "模拟步长必须是 60 的整数倍")
    public boolean isStepAligned() {
        return stepSeconds == null || stepSeconds % 60 == 0;
    }
}

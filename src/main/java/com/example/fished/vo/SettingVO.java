package com.example.fished.vo;

import com.example.fished.entity.SimSetting;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
public class SettingVO {
    private Long id;
    private Long pondId;
    private Boolean safetyVetoEnabled;//安全否决
    private Integer stepSeconds;//模拟步长
    private Integer sampleIntervalSec;//采样间隔
    private LocalDateTime updateTime;

    public static SettingVO from(SimSetting entity) {
        SettingVO vo = new SettingVO();
        BeanUtils.copyProperties(entity,vo);
        return vo;
    }
}

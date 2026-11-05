package com.example.fished.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.fished.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
/** 系统设置 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sim_setting")
public class SimSetting extends BaseEntity {
    private Long pondId;

    private Boolean safetyVetoEnabled;

    private Integer stepSeconds;

    private Integer sampleIntervalSec;
}

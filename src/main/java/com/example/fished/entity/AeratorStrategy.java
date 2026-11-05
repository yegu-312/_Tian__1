package com.example.fished.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.fished.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

//增氧策略
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aerator_strategy")
public class AeratorStrategy extends BaseEntity {

    private Long pondId;
    private String mode;//控制模式

    private BigDecimal onThreshold;
    private BigDecimal offThreshold;
    private BigDecimal safetyFloor;

    private Integer predictMinutes;
    private Boolean manualOn;//手动开关
}

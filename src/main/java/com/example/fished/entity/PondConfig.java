package com.example.fished.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.fished.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 鱼塘水体参数配置，对应表 pond_config。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pond_config")
public class PondConfig extends  BaseEntity {

    private String pondName;

    private String temperatureMode;

    private BigDecimal baseTemperature;
    private BigDecimal dailyAmplitude;//水温幅度
    private BigDecimal doMin;
    private BigDecimal doMax;
    private BigDecimal produceCoefficient;
    private BigDecimal consumeCoefficient;
    private BigDecimal aeratorRate;//产氧率

}

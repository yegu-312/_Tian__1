package com.example.fished.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.fished.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("electricity_price")
public class ElectricityPrice extends BaseEntity {

    private Long pondId;

    private String periodType;//存“峰、平、谷”
    private Integer startHour;
    private Integer endHour;
    private BigDecimal price;
}

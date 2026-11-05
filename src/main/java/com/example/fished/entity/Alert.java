package com.example.fished.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.fished.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper =true)
@TableName("alert")
public class Alert extends BaseEntity {

    private Long pondId;

    private String alertType;

    private String level;
    private String status;
    private String message;

    private BigDecimal doValue;
    private LocalDateTime pondTime;
    private LocalDateTime ackTime;//真实时间
    private String ackBy;//确认
}

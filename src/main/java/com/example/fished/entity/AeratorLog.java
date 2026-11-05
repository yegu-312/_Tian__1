package com.example.fished.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
//增氧机开关记录
@Data
@TableName("aerator_log")
public class AeratorLog implements Serializable {
    private static final long serialVersionUID=1L;

    @TableId(type=IdType.AUTO)//自增id
    private Long id;

    private Long pondId;

    private String action;//on/off
    private String triggerReason;//触发原因
    private BigDecimal doValue;//氧气含量
    private LocalDateTime pondTime;

    private String operator;

    @TableField(fill=FieldFill.INSERT)//自动填充真实时间
    private LocalDateTime createTime;
}

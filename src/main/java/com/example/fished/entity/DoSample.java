package com.example.fished.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/** 溶氧采样历史（降采样落库用） */
@Data
@TableName("do_sample")
public class DoSample implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type= IdType.AUTO)
    private Long id;

    private Long pondId;

    private LocalDateTime pondTime;
    private BigDecimal doValue;
    private BigDecimal temperature;
    private Boolean aeratorOn;

    @TableField(fill= FieldFill.INSERT)
    private LocalDateTime createTime;
}

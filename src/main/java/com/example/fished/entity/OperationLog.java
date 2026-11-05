package com.example.fished.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

//操作日志
@Data
@TableName("operation_log" )
public class OperationLog implements Serializable {
    private static final long serialVersionUID=1L;

    @TableId(type= IdType.AUTO)
    private Long id;

    private String module;
    private String action;
    private String detail;
    private String operator;

    private LocalDateTime pondTime;

    @TableField(fill= FieldFill.INSERT)
    private LocalDateTime createTime;
}

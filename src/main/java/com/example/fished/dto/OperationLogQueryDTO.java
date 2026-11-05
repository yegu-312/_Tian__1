package com.example.fished.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class OperationLogQueryDTO {
    @Min(value = 1, message = "页码从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数至少 1")
    @Max(value = 200, message = "每页最多 200 条")
    private Integer size = 10;

    private String module;
    private String operator;
}

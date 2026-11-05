package com.example.fished.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class AlertQueryDTO {

    @Min(value = 1,message="页码从1开始")
    private Integer page=1;
    @Min(value=1,message = "每页条数至少1")
    @Max(value=200,message="每页最多200条")
    private Integer size=10;

    private Long pondId;
    private String level;
    private String status;
    private String alertType;
}

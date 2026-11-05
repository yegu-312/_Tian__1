package com.example.fished.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class AeratorLogQueryDTO {
    @Min(value = 1, message = "页码从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数至少 1")
    @Max(value = 200, message = "每页最多 200 条")
    private Integer size = 10;

    private Long pondId;
    private String action;
    //"按 ISO 日期时间格式解析，也就是yyyy-MM-dd'T'HH:mm:ss"
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}

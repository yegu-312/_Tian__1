package com.example.fished.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 历史记录查询参数。时间均为鱼塘仿真时间（pond_time）。
 * pondId 为空时使用第一个鱼塘；from/to 为空时默认最近 24 小时。
 */
@Data
public class HistoryQueryDTO {

    private Long pondId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime to;
}

package com.example.fished.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 增氧机电费统计：按鱼塘、按天汇总实际开机时长，并按分时电价估算电费。
 */
@Data
public class ElectricityCostVO {

    private Long pondId;
    private String pondName;
    /** 计算使用的增氧机额定功率（kW） */
    private BigDecimal powerKw;
    /** 统计截止的鱼塘时间 */
    private LocalDateTime pondTime;
    private boolean aeratorOn;

    private String currentPeriodType;
    private String currentPeriodDesc;
    private BigDecimal currentPrice;

    private Summary today;
    private Summary last7Days;
    private Summary last30Days;
    /** 最近 7 天（含今天），按日期升序 */
    private List<Daily> daily;

    public record Summary(BigDecimal energyKwh,
                          BigDecimal cost,
                          long runtimeMinutes,
                          BigDecimal peakCost,
                          BigDecimal flatCost,
                          BigDecimal valleyCost) {
    }

    public record Daily(String date,
                        BigDecimal energyKwh,
                        BigDecimal cost,
                        long runtimeMinutes,
                        BigDecimal peakCost,
                        BigDecimal flatCost,
                        BigDecimal valleyCost) {
    }
}

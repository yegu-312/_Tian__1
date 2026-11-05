package com.example.fished.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StrategyUpdateDTO {

    @NotBlank(message="控制模式不能为空")
    private String mode;

    @NotNull(message="开机阈值不能为空")
    @DecimalMin(value = "0.0", message = "开机阈值不能为负")
    @DecimalMax(value = "20.0", message = "开机阈值不能超过 20 mg/L")
    private BigDecimal onThreshold;

    @NotNull(message="关机阈值不能为空")
    @DecimalMin(value = "0.0", message = "关机阈值不能为负")
    @DecimalMax(value = "20.0", message = "关机阈值不能超过 20 mg/L")
    private BigDecimal offThreshold;

    @NotNull(message="安全下限不能为空")
    @DecimalMin(value = "0.0", message = "安全下限不能为负")
    @DecimalMax(value = "20.0", message = "安全下限不能超过 20 mg/L")
    private BigDecimal safetyFloor;

    @NotNull(message="提前开机分钟数不能为空")
    @DecimalMin(value = "0", message = "提前开机分钟数不能为负")
    @DecimalMax(value = "1440", message = "提前开机分钟数最多1440(一天)")
    private Integer predictMinutes;

    @NotNull(message = "手动开关状态不能为空")
    private Boolean manualOn;

}

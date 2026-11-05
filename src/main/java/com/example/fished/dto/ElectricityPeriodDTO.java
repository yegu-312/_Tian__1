package com.example.fished.dto;

import com.example.fished.common.BusinessException;
import com.example.fished.common.enums.ElectricityPeriodEnum;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
//配置增氧机的分时电价
@Data
public class ElectricityPeriodDTO {
    @NotBlank(message = "时段类型不能为空（peak / flat / valley）")
    private String periodType;

    @NotNull(message = "开始小时不能为空")
    @Min(value = 0, message = "开始小时最小是 0")
    @Max(value = 23, message = "开始小时最大是 23")
    private Integer startHour;

    @NotNull(message = "结束小时不能为空")
    @Min(value = 1, message = "结束小时最小是 1")
    @Max(value = 24, message = "结束小时最大是 24")
    private Integer endHour;

    @NotNull(message = "单价不能为空")
    @DecimalMin(value = "0.000", message = "单价不能为负")
    @Digits(integer = 3, fraction = 3, message = "单价最多 3 位整数、3 位小数")
    private BigDecimal price;

    public ElectricityPeriodEnum periodTypeEnum(){
        return ElectricityPeriodEnum.getByCode(periodType)
                .orElseThrow(()->new BusinessException("未知的电价时段类型:"+periodType));
    }
}

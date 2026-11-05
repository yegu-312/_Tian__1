package com.example.fished.dto;

import com.example.fished.common.enums.TemperatureModeEnum;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PondConfigUpdateDTO {

    @NotBlank(message = "鱼塘名称不能为空")
    @Size(max = 64, message = "鱼塘名称最多 64 个字符")
    private String pondName;

    /** diurnal = 日内曲线，fixed = 固定值 */
    @NotBlank(message = "温度模式不能为空")
    private String temperatureMode;

    @NotNull(message = "基准温度不能为空")
    @DecimalMin(value = "0.0", message =
            "基准温度不能为负")
    @DecimalMax(value = "45.0", message =
            "基准温度不能超过 45℃")
    private BigDecimal baseTemperature;

    @NotNull(message = "昼夜温差振幅不能为空")
    @DecimalMin(value = "0.0", message ="振幅不能为负")
    @DecimalMax(value = "15.0", message = "振幅最多15℃")
    private BigDecimal dailyAmplitude;

    @NotNull(message = "溶氧下限不能为空")
    @DecimalMin(value = "0.0", message ="溶氧下限不能为负")
    private BigDecimal doMin;

    @NotNull(message = "溶氧上限不能为空")
    @DecimalMin(value = "0.0", message =
            "溶氧上限不能为负")
    private BigDecimal doMax;

    @NotNull(message = "产氧系数不能为空")
    @DecimalMin(value = "0.0", message = "产氧系数不能为负")
    @DecimalMax(value = "5.0", message = "产氧系数最多5.0")
    private BigDecimal produceCoefficient;

    @NotNull(message = "耗氧系数不能为空")
    @DecimalMin(value = "0.0", message = "耗氧系数不能为负")
    @DecimalMax(value = "5.0", message = "耗氧系数最多5.0")
    private BigDecimal consumeCoefficient;

    @NotNull(message = "增氧速率不能为空")
    @DecimalMin(value = "0.0", message = "增氧速率不能为负")
    @DecimalMax(value = "10.0", message = "增氧速率最多 10.0")
    private BigDecimal aeratorRate;

    @AssertTrue(message="溶氧上限必须大于下限")
    public boolean isDoRangeValid() {
        if (doMin == null || doMax == null) {
            return true;
        }
        return doMax.compareTo(doMin)>0;
    }
    @AssertTrue(message = "温度模式只能是diurnal或fixed")
    public boolean isTemperatureModeValid(){
        return temperatureMode !=null&& TemperatureModeEnum.getByCode(temperatureMode).isPresent();
    }
}

package com.example.fished.vo;

import com.example.fished.common.enums.TemperatureModeEnum;
import com.example.fished.entity.PondConfig;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
//鱼塘配置的视图对象
@Data
public class PondConfigVO {
    private Long id;
    private String pondName;
    private String temperatureMode;//水温变化模式/日内曲线
    private String temperatureModeDesc;
    private BigDecimal baseTemperature;
    private BigDecimal dailyAmplitude;
    private BigDecimal doMin;
    private BigDecimal doMax;
    private BigDecimal produceCoefficient;
    private BigDecimal consumeCoefficient;
    private BigDecimal aeratorRate;

    public static PondConfigVO from(PondConfig entity){
        PondConfigVO vo = new PondConfigVO();
        BeanUtils.copyProperties(entity,vo);

        vo.setTemperatureModeDesc(TemperatureModeEnum.getByCode(entity.getTemperatureMode())
                .map(TemperatureModeEnum::getDesc)
                .orElse(entity.getTemperatureMode()));
        return vo;
    }
}

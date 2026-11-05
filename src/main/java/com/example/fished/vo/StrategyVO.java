package com.example.fished.vo;

import com.example.fished.common.enums.AeratorModeEnum;
import com.example.fished.entity.AeratorStrategy;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;

@Data
public class StrategyVO {
    private Long id;
    private Long pondId;

    private String mode;//模式
    private String modeDesc;

    private BigDecimal onThreshold;
    private BigDecimal offThreshold;
    private BigDecimal safetyFloor;
    private Integer predictMinutes;
    private Boolean manualOn;

    public static StrategyVO from(AeratorStrategy entity) {
        StrategyVO vo = new StrategyVO();
        BeanUtils.copyProperties(entity,vo);
        //转换mode字段成为中文记录
        vo.setModeDesc(AeratorModeEnum.getByCode(entity.getMode())
                .map(AeratorModeEnum::getDesc).orElse(entity.getMode()));
        return vo;
    }
}

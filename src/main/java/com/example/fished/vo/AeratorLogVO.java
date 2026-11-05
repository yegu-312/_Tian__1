package com.example.fished.vo;

import com.example.fished.common.enums.AeratorActionEnum;
import com.example.fished.entity.AeratorLog;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AeratorLogVO {

    private Long id;
    private Long pondId;
    private String action;
    private String actionDesc;
    private String triggerReason;
    private BigDecimal doValue;
    private LocalDateTime pondTime;
    private String operator;
    private LocalDateTime createTime;

    public static AeratorLogVO from(AeratorLog entity){
        AeratorLogVO vo = new AeratorLogVO();

        BeanUtils.copyProperties(entity,vo);

        vo.setActionDesc(AeratorActionEnum.getByCode(entity.getAction())
                .map(AeratorActionEnum::getDesc).orElse(entity.getAction()));
        return vo;
    }
}

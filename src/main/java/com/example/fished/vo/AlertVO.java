package com.example.fished.vo;

import com.example.fished.common.enums.AlertLevelEnum;
import com.example.fished.common.enums.AlertStatusEnum;
import com.example.fished.common.enums.AlertTypeEnum;
import com.example.fished.entity.Alert;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AlertVO {

    private Long id;
    private Long pondId;

    private String alertType;
    private String alertTypeDesc;//类型中文
    private String level;
    private String levelDesc;
    private String status;
    private String statusDesc;

    private String message;
    private BigDecimal doValue;//溶氧值
    private LocalDateTime pondTime;//鱼塘时间
    private LocalDateTime ackTime;//确认时间
    private String ackBy;//确认人
    private LocalDateTime createTime;
    //实体转换,转换中文
    public static AlertVO from(Alert entity) {
        AlertVO vo = new AlertVO();

        BeanUtils.copyProperties(entity,vo);

        vo.setAlertTypeDesc(AlertTypeEnum.getByCode(entity.getAlertType())
                .map(AlertTypeEnum::getDesc).orElse(entity.getAlertType()));
        vo.setLevelDesc(AlertLevelEnum.getByCode(entity.getLevel())
                .map(AlertLevelEnum::getDesc).orElse(entity.getLevel()));
        vo.setStatusDesc(AlertStatusEnum.getByCode(entity.getStatus())
                .map(AlertStatusEnum::getDesc).orElse(entity.getStatus()));
        return vo;
    }
}

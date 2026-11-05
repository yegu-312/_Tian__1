package com.example.fished.vo;

import com.example.fished.common.enums.OperationActionEnum;
import com.example.fished.common.enums.OperationModuleEnum;
import com.example.fished.entity.OperationLog;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;

@Data
public class OperationLogVO {

    private Long id;
    private String module;//模块
    private String moduleDesc;//模块中文版
    private String action;
    private String actionDesc;
    private String detail;
    private String operator;
    private LocalDateTime pondTime;//鱼塘时间
    private LocalDateTime createTime;

    public static OperationLogVO from(OperationLog entity){
        OperationLogVO vo = new OperationLogVO();
        BeanUtils.copyProperties(entity,vo);
        //中文描述
        vo.setModuleDesc(OperationModuleEnum.getByCode(entity.getModule())
                .map(OperationModuleEnum::getDesc).orElse(entity.getModule()));
        vo.setActionDesc(OperationActionEnum.getByCode(entity.getAction())
                .map(OperationActionEnum::getDesc).orElse(entity.getAction()));

        return vo;
    }
}

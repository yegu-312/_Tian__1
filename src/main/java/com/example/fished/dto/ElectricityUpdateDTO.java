package com.example.fished.dto;

import com.example.fished.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.lang.String.format;

@Data
public class ElectricityUpdateDTO {
    @NotEmpty(message = "电价时段不能为空")
    @Valid
    private List<ElectricityPeriodDTO> periods;
    //校验电价时段是否合法
    public static void checkWholeDay(List<ElectricityPeriodDTO> periods){
        if(periods==null||periods.isEmpty()){
            throw new BusinessException("电价时段不能为空");
        }
        for(ElectricityPeriodDTO p:periods){
            p.periodTypeEnum();
            if(p.getStartHour()==null||p.getEndHour()==null){
                throw new BusinessException("时段的小时数不能为空");
            }
            if(p.getEndHour()<=p.getStartHour()){
                throw new BusinessException(format(
                        "时段的结束小时必须大于开始小时：%d →%d（跨午夜请拆成两段，如 23-24 和 0-7）",
                        p.getStartHour(),p.getEndHour()));
            }
        }

        List<ElectricityPeriodDTO> sorted = new ArrayList<>(periods);//拷贝一份
        sorted.sort(Comparator.comparingInt(ElectricityPeriodDTO::getStartHour));
        int cursor=0;//游标从零开始校验
        for (ElectricityPeriodDTO p : sorted) {
            if (p.getStartHour() > cursor) {
                throw new BusinessException(String.format(
                        "%d 点到 %d 点没有配置电价", cursor, p.getStartHour()));
            }
            if (p.getStartHour() < cursor) {
                throw new BusinessException(String.format("%d 点被重复配置（上一个时段已经盖到 %d 点）",
                        p.getStartHour(), cursor));
            }
            cursor = p.getEndHour();
        }

        if (cursor < 24) {
                throw new BusinessException(String.format("%d 点到 24 点没有配置电价", cursor));
        }
    }
}

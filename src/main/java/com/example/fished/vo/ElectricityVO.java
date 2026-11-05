package com.example.fished.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ElectricityVO {

    private List<Period> periods;

    private List<BigDecimal> hourlyPrices;
    private Current current;//当前时间在哪个区段
    //这段时间电价配置
    public record Period(String periodType,String periodDesc,int startHour,
                         int endHour,BigDecimal price){

    }
    //当前时间电价配置
    public record Current(String periodType, String periodDesc,
                          BigDecimal price, LocalDateTime pondTime){

    }
}

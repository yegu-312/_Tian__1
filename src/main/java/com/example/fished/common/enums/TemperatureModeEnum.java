package com.example.fished.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum TemperatureModeEnum {

    DIURNAL("diurnal","日内曲线"),
    FIXED("fixed","固定值");

    private final String code;
    private final String desc;

    TemperatureModeEnum(String code,String desc){
        this.code = code;
        this.desc = desc;
    }

   public static Optional<TemperatureModeEnum> getByCode(String code){
        return Arrays.stream(values())
                .filter(e->e.getCode().equals(code))
                .findFirst();
   }
}

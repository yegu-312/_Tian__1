package com.example.fished.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum AeratorModeEnum {
    AUTO("auto","自动"),
    MANUAL("manual","手动");
    private String code;
    private String desc;
    AeratorModeEnum(String code,String desc){
        this.code = code;
        this.desc = desc;
    }

    public static Optional<AeratorModeEnum> getByCode(String code){
        return Arrays.stream(values()).filter(e->
                e.getCode().equals(code)).findFirst();
    }
}

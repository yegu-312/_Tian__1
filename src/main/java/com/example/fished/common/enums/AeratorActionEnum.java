package com.example.fished.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum AeratorActionEnum {
    ON("on","开机"),
    OFF("off","关机");

    private final String code;
    private final String desc;

    AeratorActionEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public static Optional<AeratorActionEnum> getByCode(String code){
        return Arrays.stream(values()).filter(e->e.getCode().equals(code)).findFirst();
    }

    public static AeratorActionEnum of(boolean on){
        return on ? ON: OFF;
    }
}

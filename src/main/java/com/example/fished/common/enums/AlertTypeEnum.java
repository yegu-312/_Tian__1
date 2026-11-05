package com.example.fished.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum AlertTypeEnum {
    DO_SAFETY_FLOOR("do_safety_floor","溶氧跌破安全下限"),
    DO_ON_THRESHOLD("do_on_threshold","溶氧低于开机阈值");

    private final String code;
    private final String desc;
    AlertTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static Optional<AlertTypeEnum> getByCode(String code){
        return Arrays.stream(values()).filter(e->e.getCode().equals(code)).findFirst();
    }
}

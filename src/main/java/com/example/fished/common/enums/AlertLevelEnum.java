package com.example.fished.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum AlertLevelEnum {
    INFO("info", "提示"),
    WARN("warn", "警告"),
    URGENT("urgent", "紧急");

    private String code;
    private String desc;
    AlertLevelEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public static Optional<AlertLevelEnum> getByCode(String code){
        return Arrays.stream(values()).filter(e->e.getCode().equals(code)).findFirst();
    }
}

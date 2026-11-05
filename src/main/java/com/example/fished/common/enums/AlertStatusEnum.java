package com.example.fished.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum AlertStatusEnum {
    PENDING("pending", "待确认"),
    ACKNOWLEDGED("acknowledged", "已确认"),
    RECOVERED("recovered","已恢复");

    private String code;
    private String desc;
    AlertStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static Optional<AlertStatusEnum> getByCode(String code){
        return Arrays.stream(values()).filter(e->e.getCode().equals(code)).findFirst();
    }
}

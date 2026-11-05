package com.example.fished.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum ElectricityPeriodEnum {
    PEAK("peak", "峰"),
    FLAT("flat", "平"),
    VALLEY("valley", "谷");

    private final String code;
    private final String desc;

    ElectricityPeriodEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static Optional<ElectricityPeriodEnum>
    getByCode(String code) {
        return Arrays.stream(values()).filter(e -> e.getCode().equals(code)).findFirst();
    }
}

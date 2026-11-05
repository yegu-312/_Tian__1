package com.example.fished.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum OperationModuleEnum {
    POND_CONFIG("pond_config","水体参数"),
    AERATOR("aerator","增氧控制"),
    SETTING("setting","系统设置"),
    CLOCK("clock","鱼塘时钟"),
    ALERT("alert","告警中心"),
    PRICE("price","电价管理");
    private final String code;
    private final String desc;

    OperationModuleEnum(String code,String desc){
        this.code = code;
        this.desc = desc;
    }
    public static Optional<OperationModuleEnum> getByCode(String code){
        return Arrays.stream(values()).filter(e->e.getCode().equals(code)).findFirst();
    }
}

package com.example.fished.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum OperationActionEnum {
    CREATE("create","新增"),
    DELETE("delete","删除"),
    UPDATE("update","修改配置"),
    RESET("reset","重置系统"),
    MANUAL_ON("manual_on","手动开机"),
    MANUAL_OFF("manual_off","手动关机"),
    PAUSE("pause","暂停运行"),
    RESUME("resume","恢复运行"),
    SPEED("speed","调整倍速"),
    SET_TIME("set_time","设置鱼塘时间"),
    ACK("ack","确认告警"),
    EXCHANGE_WATER("exchange_water","换水"),
    ADJUST_WATER("adjust_water","调水");

    private final String code;
    private final String desc;
    OperationActionEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    //枚举找对应的值
    public static Optional<OperationActionEnum> getByCode(String code){
        return Arrays.stream(values()).filter(e->e.getCode().equals(code)).findFirst();
    }
}

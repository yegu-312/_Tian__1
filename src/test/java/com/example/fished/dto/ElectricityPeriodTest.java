package com.example.fished.dto;

import com.example.fished.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;

class ElectricityPeriodTest {
    private static ElectricityPeriodDTO p(String type,int start,int end){
        ElectricityPeriodDTO dto = new ElectricityPeriodDTO();
        dto.setPeriodType(type);
        dto.setStartHour(start);
        dto.setEndHour(end);
        dto.setPrice(new BigDecimal("0.70"));
        return dto;
    }

    @Test
    @DisplayName("正好覆盖 0~24 且不重叠 → 通过")
    void wholeDayIsFine() {
        assertDoesNotThrow(() ->
                ElectricityUpdateDTO.checkWholeDay(List.of(
                        p("valley", 0, 7),
                        p("flat",  7, 10),
                        p("peak", 10, 15),
                        p("flat", 15, 24))));
    }

    @Test
    @DisplayName("乱序提交也能通过 —— 校验前会先排序")
    void orderDoesNotMatter() {
        assertDoesNotThrow(() ->
                ElectricityUpdateDTO.checkWholeDay(List.of(
                        p("peak", 10, 15),
                        p("valley", 0, 7),
                        p("flat", 15, 24),
                        p("flat", 7, 10))));
    }
    @Test
    @DisplayName("中间有空档 → 报出是哪一段缺了")
    void gapIsRejected() {
        BusinessException e = assertThrows(BusinessException.class,
                        () -> ElectricityUpdateDTO.checkWholeDay(List.of(
                                        p("valley", 0, 7),
                                        p("flat", 10, 24))));
        assertTrue(e.getMessage().contains("7 点到 10 点"), "实际消息：" + e.getMessage());
    }

    @Test
    @DisplayName("尾巴没盖住 → 报出从哪一点开始缺")
    void tailGapIsRejected() {
        BusinessException e =
                assertThrows(BusinessException.class,
                        () -> ElectricityUpdateDTO.checkWholeDay(List.of(
                                        p("valley", 0, 7),
                                        p("flat", 7, 20))));
        assertTrue(e.getMessage().contains("20 点到 24 点"), "实际消息：" + e.getMessage());
    }

    @Test
    @DisplayName("时段重叠 → 被拦下")
    void overlapIsRejected() {
        BusinessException e =
                assertThrows(BusinessException.class,
                        () -> ElectricityUpdateDTO.checkWholeDay(List.of(
                                        p("valley", 0, 10),
                                        p("flat", 7, 24))));

        assertTrue(e.getMessage().contains("重复配置"), "实际消息：" + e.getMessage());
    }

    @Test
    @DisplayName("没从 0 点开始 → 被拦下")
    void mustStartAtZero() {
        BusinessException e =
                assertThrows(BusinessException.class,
                        () -> ElectricityUpdateDTO.checkWholeDay(List.of(p("flat", 5, 24))));
        assertTrue(e.getMessage().contains("0 点到 5 点"), "实际消息：" + e.getMessage());
    }

    @Test
    @DisplayName("结束小时不大于开始小时 →给出可操作的提示")
    void endMustBeAfterStart() {
        BusinessException e =
                assertThrows(BusinessException.class,
                        () -> ElectricityUpdateDTO.checkWholeDay(List.of(p("valley", 23, 7), p("flat", 7, 23))));
        assertTrue(e.getMessage().contains("跨午夜"), "实际消息：" + e.getMessage());
    }

    @Test
    @DisplayName("不认识的时段类型 → 被拦下")
    void unknownTypeIsRejected() {
        BusinessException e =
                assertThrows(BusinessException.class,
                        () -> ElectricityUpdateDTO.checkWholeDay(List.of(p("super_peak", 0, 24))));
        assertTrue(e.getMessage().contains("未知的电价时段类型"), "实际消息：" + e.getMessage());
    }

    @Test
    @DisplayName("空列表 → 被拦下")
    void emptyIsRejected() {
        assertThrows(BusinessException.class, () ->
                ElectricityUpdateDTO.checkWholeDay(List.of()));
        assertThrows(BusinessException.class, () ->
                ElectricityUpdateDTO.checkWholeDay(null));
    }

}

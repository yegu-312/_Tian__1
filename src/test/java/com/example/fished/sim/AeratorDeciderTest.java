package com.example.fished.sim;

import com.example.fished.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AeratorDeciderTest {
    private final AeratorDecider decider = new AeratorDecider();

    private static PondSnapshot snap(double doValue, boolean aeratorOn) {
        return new PondSnapshot(
                LocalDateTime.of(2026, 1, 1, 12, 0),
                doValue, 25.0, aeratorOn, false, 1,0L);
    }

    private static StrategyParams auto(boolean safetyVetoEnabled) {
        return new StrategyParams("auto", 4.0, 6.0
                , 2.5, 0, false, safetyVetoEnabled);
    }

    private static StrategyParams manual(boolean manualOn, boolean safetyVetoEnabled) {
        return new StrategyParams("manual", 4.0, 6.0,
                2.5, 0,  manualOn, safetyVetoEnabled);
    }

    private static StrategyParams autoWithPredict() {
        return new StrategyParams("auto", 4.0, 6.0,
                2.5, 30, false, true);
    }


    @Test
    @DisplayName("★回差：溶氧在 4.0~6.0之间时保持原状，同一个值结果取决于历史状态")
    void hysteresisHoldsStateInDeadBand() {
        PondSnapshot wasOn = snap(5.5, true);
        Decision stillOn = decider.decide(wasOn,
                auto(true), null);
        assertTrue(stillOn.on(), "原本开着，5.5在不动区，应该继续开着");
                assertFalse(stillOn.changed(), "不应该产生状态变化");

        PondSnapshot wasOff = snap(5.5, false);
        Decision stillOff = decider.decide(wasOff,
                auto(true), null);
        assertFalse(stillOff.on(), "原本关着，5.5在不动区，应该继续关着");
                assertFalse(stillOff.changed(), "不应该产生状态变化");
    }

    @Test
    @DisplayName("低于开机阈值时自动开启")
    void autoTurnsOnBelowOnThreshold() {
        Decision d = decider.decide(snap(3.5, false),
                auto(true), null);
        assertTrue(d.on());
        assertTrue(d.changed());
        assertNotNull(d.reason(),
                "变化时必须有原因，要写进 aerator_log");
    }

    @Test
    @DisplayName("高于关机阈值时自动关闭")
    void autoTurnsOffAboveOffThreshold() {
        Decision d = decider.decide(snap(7.0, true),
                auto(true), null);
        assertFalse(d.on());
        assertTrue(d.changed());
        assertNotNull(d.reason());
    }


    @Test
    @DisplayName("安全否决优先级最高，能推翻手动关机")
    void safetyVetoOverridesManualOff() {
        Decision d = decider.decide(snap(1.5, false),
                manual(false, true), null);
        assertTrue(d.on(), "溶氧 1.5 低于安全下限2.5，必须强制开机");
        assertTrue(d.changed());
        assertTrue(d.reason().contains("安全否决"), "原因里应写明是安全否决，实际：" + d.reason());
    }

    @Test
    @DisplayName("关掉安全否决后，手动模式说了算（同样1.5 的溶氧）")
    void manualOffRespectedWhenVetoDisabled() {
        Decision d = decider.decide(snap(1.5, false),
                manual(false, false), null);
        assertFalse(d.on(), "否决关掉了，手动模式要求关闭，就应该关着");
    }

    @Test
    @DisplayName("手动模式下，手动开启能打开增氧机")
    void manualOnTurnsAeratorOn() {
        Decision d = decider.decide(snap(5.0, false),
                manual(true, true), null);
        assertTrue(d.on());
        assertTrue(d.changed());
    }

    @Test
    @DisplayName("手动模式下，已经处于目标状态时不做任何事")
    void manualKeepsWhenAlreadyInTargetState() {
        Decision d = decider.decide(snap(5.0, true),
                manual(true, true), null);
        assertTrue(d.on());
        assertFalse(d.changed(), "已经是开启状态，不该再产生一次变化");
        assertNull(d.reason());
    }



    @Test
    @DisplayName("★预测提前开机：当前溶氧没到阈值，但预测会跌破，提前开启")
    void predictivePreemptiveOn() {
        Decision withoutPredict =
                decider.decide(snap(5.0, false), autoWithPredict(),
                        null);
        assertFalse(withoutPredict.on(), "没有预测数据时，5.0 不该开机");

        Decision withPredict =
                decider.decide(snap(5.0, false), autoWithPredict(),
                        3.5);
        assertTrue(withPredict.on(), "预测到会跌破阈值，应该提前开机");

        assertTrue(withPredict.reason().contains("预测"), "实际：" + withPredict.reason());
    }

    @Test
    @DisplayName("预测值没跌破阈值时，不提前开机")
    void predictiveDoesNotFireWhenPredictionIsSafe()
    {
        Decision d = decider.decide(snap(5.0, false),
                autoWithPredict(), 4.5);
        assertFalse(d.on());
    }

    @Test
    @DisplayName("预测分钟数为 0时，预测逻辑整个不生效")
    void predictiveDisabledWhenMinutesIsZero() {

        Decision d = decider.decide(snap(5.0, false),
                auto(true), 3.5);
        assertFalse(d.on(), "predictMinutes=0表示不启用预测");
    }

    @Test
    @DisplayName("已经开着的时候，预测逻辑不重复触发")
    void predictiveDoesNotRefireWhenAlreadyOn() {
        Decision d = decider.decide(snap(5.0, true),
                autoWithPredict(), 3.5);
        assertTrue(d.on());
        assertFalse(d.changed(), "已经开着，不该再产生一次变化");
    }

    @Test
    @DisplayName("★关机阈值不大于开机阈值时，构造参数直接失败")
    void invalidThresholdsRejected() {
        BusinessException e =
                assertThrows(BusinessException.class,
                        () -> new StrategyParams("auto", 6.0,
                                4.0, 2.5, 0, false, true));

        assertTrue(e.getMessage().contains("关机阈值"), "实际：" + e.getMessage());
    }

    @Test
    @DisplayName("安全下限高于开机阈值时，构造参数直接失败")
    void safetyFloorAboveOnThresholdRejected() {
        assertThrows(BusinessException.class,
                () -> new StrategyParams("auto", 4.0,
                        6.0, 5.0, 0, false, true));
    }
}

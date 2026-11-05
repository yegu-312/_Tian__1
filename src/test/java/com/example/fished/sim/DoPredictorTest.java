package com.example.fished.sim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DoPredictorTest {
    private static final SimParams PARAMS=SimParams.defaults();
    private static final LocalDateTime NOON=LocalDateTime.of(2026,9,22,12,0);
    private static final LocalDateTime EVENING=LocalDateTime.of(2026,9,22,20,0);

    @Test
    @DisplayName("预测是确定性的：同样输入跑两次，结果逐点完全一致")
    void predictionIsDeterministic(){
        List<DoPoint> a = DoPredictor.predict(6.0, NOON, 0L, false, PARAMS, 30);
        List<DoPoint> b = DoPredictor.predict(6.0, NOON, 0L, false, PARAMS, 30);

        assertEquals(a,b);
        assertEquals(30,a.size());
    }
    @Test
    @DisplayName("横轴从当前+1开始，到当前+minutesAhead 结束，不含当前那一点")
    void axisStartsRightAfterNow(){
        List<DoPoint> points= DoPredictor.predict(6.0, NOON, 100L, false, PARAMS, 30);
        assertEquals(101L,points.get(0).minuteOffset());
        assertEquals(130L,points.get(points.size()-1).minuteOffset());
    }
    @Test
    @DisplayName("白天开着增氧机，60分钟后必然比现在高")
    void risesWhenAeratingInDaylight() {
        List<DoPoint> points = DoPredictor.predict(4.0, NOON, 0L, true, PARAMS, 60);

        assertTrue(points.get(points.size() - 1).doValue() > 4.0, "正午开着增氧机应该在涨，实际 " + points.get(points.size() - 1).doValue());
    }

    @Test
    @DisplayName("夜里关着增氧机，4小时后必然比现在低")
    void fallsOvernightWithoutAeration() {
        List<DoPoint> points = DoPredictor.predict(9.0, EVENING, 0L, false, PARAMS, 240);
        assertTrue(points.get(points.size() - 1).doValue() < 9.0);
    }


    @Test
    @DisplayName("predictMin只看未来，不把当前值算进最低点")
    void predictMinIgnoresCurrent() {
        double min = DoPredictor.predictMin(9.0, EVENING, false, PARAMS, 240);
        assertTrue(min < 9.0, "实际 " + min);
    }

    @Test
    @DisplayName("预测不会跑出物理边界：永远 >= 0")
    void neverGoesNegative() {
        List<DoPoint> points = DoPredictor.predict(0.05, LocalDateTime.of(2026, 9, 22,
                        22, 0), 0L, false, PARAMS, 600);

        assertTrue(points.stream().allMatch(p -> p.doValue() >= 0.0));
    }

    @Test
    @DisplayName("minutesAhead=0时返回空列表，predictMin 退回当前值")
    void zeroHorizonIsSafe() {
        assertTrue(DoPredictor.predict(6.0, NOON, 0L,
                false, PARAMS, 0).isEmpty());
        assertEquals(6.0, DoPredictor.predictMin(6.0,
                NOON, false, PARAMS, 0), 1e-9);
    }
}

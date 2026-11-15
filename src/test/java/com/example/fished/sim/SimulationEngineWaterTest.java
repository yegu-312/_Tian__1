package com.example.fished.sim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineWaterTest {

    private static SimulationEngine engine() {
        SimParams params = new SimParams("fixed", 25.0, 0.0,
                3.0, 12.0, 0.600, 0.090, 1.200);
        return new SimulationEngine(1L, params, StrategyParams.defaults(),
                60, 60, LocalDateTime.of(2026, 11, 15, 10, 0), 6.0);
    }

    @Test
    @DisplayName("换水把 pH 稀释向水源值，并抬高溶氧")
    void exchangeDilutesPhAndRaisesDo() {
        SimulationEngine engine = engine();
        PondSnapshot before = engine.getSnapshot();

        SimulationEngine.WaterActionResult result = engine.exchangeWater(0.5);

        double expectedPh = before.ph() * 0.5 + Physics.SOURCE_WATER_PH * 0.5;
        assertEquals(before.ph(), result.before().ph(), 1e-9);
        assertEquals(expectedPh, result.after().ph(), 0.01);
        assertTrue(result.after().currentDo() > before.currentDo(),
                "水源溶氧高于当前值时应抬高溶氧：" + before.currentDo() + " → " + result.after().currentDo());
    }

    @Test
    @DisplayName("换水比例被限制在 5%-50%")
    void exchangeRatioIsClamped() {
        PondSnapshot before = engine().getSnapshot();

        SimulationEngine.WaterActionResult half = engine().exchangeWater(0.5);
        SimulationEngine.WaterActionResult oversized = engine().exchangeWater(1.0);

        assertEquals(half.after().ph(), oversized.after().ph(), 1e-9);
        assertTrue(half.after().ph() < before.ph());
    }

    @Test
    @DisplayName("调水把 pH 拉向目标值，强度越高越接近")
    void adjustMovesPhTowardTarget() {
        SimulationEngine engine = engine();
        double before = engine.getSnapshot().ph();

        SimulationEngine.WaterActionResult weak = engine.adjustWater(7.20, 1);
        SimulationEngine.WaterActionResult strong = engine.adjustWater(7.20, 3);

        assertTrue(weak.after().ph() < before);
        assertTrue(strong.after().ph() < weak.after().ph());
        assertTrue(strong.after().ph() >= 7.20);
    }

    @Test
    @DisplayName("调水强度超出范围时按边界处理")
    void adjustStrengthIsClamped() {
        SimulationEngine engine = engine();
        SimulationEngine.WaterActionResult result = engine.adjustWater(7.20, 99);

        assertTrue(result.after().ph() >= 7.20);
        assertTrue(result.after().ph() <= Physics.DEFAULT_PH);
    }
}

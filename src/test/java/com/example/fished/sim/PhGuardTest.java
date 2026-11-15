package com.example.fished.sim;

import com.example.fished.vo.PhStatusVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhGuardTest {

    @Test
    @DisplayName("适宜区间内判定为 optimal/适宜")
    void optimalInsideComfortRange() {
        assertEquals("optimal", PhGuard.code(6.5));
        assertEquals("optimal", PhGuard.code(7.5));
        assertEquals("optimal", PhGuard.code(8.5));
        assertEquals("适宜", PhGuard.status(7.2));
    }

    @Test
    @DisplayName("低于下限偏酸，高于上限偏碱")
    void lowAndHighOutsideComfortRange() {
        assertEquals("low", PhGuard.code(6.49));
        assertEquals("偏酸", PhGuard.status(6.0));
        assertEquals("high", PhGuard.code(8.51));
        assertEquals("偏碱", PhGuard.status(9.0));
    }

    @Test
    @DisplayName("pH 状态 VO 下发统一区间与描述")
    void statusVoCarriesThresholds() {
        PhStatusVO high = PhStatusVO.from(9.0);
        assertEquals(9.0, high.value());
        assertEquals("high", high.statusCode());
        assertEquals("偏碱", high.statusDesc());
        assertEquals(6.5, high.comfortMin());
        assertEquals(8.5, high.comfortMax());
    }
}

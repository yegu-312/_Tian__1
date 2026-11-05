package com.example.fished.sim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimAssemblerTest {

    @Test
    @DisplayName("相对分钟还原成绝对鱼塘时间：横轴 90 相对 120，就是30分钟前")
    void absoluteTimeRestoresPondTime(){
        PondSnapshot anchor = new PondSnapshot(
                LocalDateTime.of(2026, 9, 22, 14, 0),
                6.0,25.0,false,false,1,120L);
        DoPoint point = new DoPoint(90L, 5.8, 24.9, false);

        assertEquals(LocalDateTime.of(2026,9,22,13,30),
                SimAssembler.absoluteTime(anchor,point));
    }
    @Test
    @DisplayName("锚点自己那一点，还原出来就是锚点的鱼塘时间")
    void anchorItselfMapsToNow(){
        LocalDateTime now = LocalDateTime.of(2026, 9, 22, 14, 0);
        PondSnapshot anchor = new PondSnapshot(now, 6.0, 25.0, false, false, 1, 120L);
        assertEquals(now,SimAssembler.absoluteTime(anchor,new DoPoint(120L,6.0,25.0,false)));
    }
}

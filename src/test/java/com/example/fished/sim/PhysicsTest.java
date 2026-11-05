package com.example.fished.sim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicsTest {
     private static SimParams fixedParams() {
         return new SimParams("fixed", 25.0, 0.0
                 , 3.0, 12.0, 0.600, 0.090, 1.200);
     }
     private static SimParams withRates(double produce,double consume){
         return new SimParams("fixed",25.0,0.0
         ,3.0,12.0,produce,consume,1.200);
     }
     private static double simulate(double startDo, LocalDateTime startTime,


                                    boolean aeratorOn,SimParams params,
                                    int steps,double dtHours){
         double doValue=startDo;
         LocalDateTime t=startTime;
         for (int i = 0; i < steps; i++) {
             t=t.plusSeconds((long)(dtHours*3600));
             doValue=Physics.nextDo(
                     doValue,
                     Physics.waterTemperature(t,params),
                     Physics.daylightFactor(t),
                     aeratorOn,
                     params,
                     dtHours,
                     0.0);
         }
         return doValue;
     }
     @Test
     @DisplayName("日照系数：夜间为0，正午为1")
     void daylightFactorIsZeroAtNightPeakAtNoon(){
         assertEquals(0.0,Physics.daylightFactor(LocalDateTime.of(2026,1,1,3,0)),1e-9);
         assertEquals(0.0,Physics.daylightFactor(LocalDateTime.of(2026,1,1,22,0)),1e-9);
         assertEquals(1.0,Physics.daylightFactor(LocalDateTime.of(2026,1,1,12,0)),1e-9);
     }
     @Test
    @DisplayName("水温越高，饱和溶氧越低（夏天更容易缺氧的物理原因）")
    void staturationDropsAsTemperatureRises(){
         assertTrue(Physics.saturationDo(15)>Physics.saturationDo(25));
         assertTrue(Physics.saturationDo(25)>Physics.saturationDo(35));

     }
     @Test
    @DisplayName("规律1：白天有光照，溶氧上升")
    void doRisesDuringDaylight(){
         double end = simulate(4.0, LocalDateTime.of(2026, 1, 1, 12, 0),
                 false, fixedParams(), 60, 1.0 / 60);
         assertTrue(end>4.0, "白天溶氧应上升，实际 "+end);
     }
     @Test
     @DisplayName("规律2：夜间没有光合作用，溶解下降")
    void doFallsAtNight(){
         double end = simulate(8.0, LocalDateTime.of(2026, 1, 1, 0, 0),
                 false, fixedParams(), 60, 1.0 / 60);
         assertTrue(end<8.0,"夜间溶氧应下降,实际"+end);
     }
    @Test
    @DisplayName("规律3：开增氧机比不开溶氧更高")
    void aeratorRaisesDissolovedOxygen(){
         LocalDateTime midnight= LocalDateTime.of(2026,1,1,0,0);
        double withAerator = simulate(3.0, midnight, true, fixedParams(), 30, 1.0 / 60);
        double without = simulate(3.0, midnight, false, fixedParams(), 30, 1.0 / 60);
        assertTrue(withAerator>without,
                "开增氧机应更高：开="+withAerator+"不开="+without);
    }
    @Test
    @DisplayName("规律4：水温越高，耗氧越快")
    void higherTemperatureConsumesFaster(){
        LocalDateTime midnight = LocalDateTime.of(2026, 1, 1, 0, 0);
        SimParams warm  = new SimParams("fixed", 32.0, 0.0,
                3.0, 12.0, 0.600, 0.090, 1.200);
        SimParams cool = new SimParams("fixed", 18.0, 0.0,
                3.0, 12.0, 0.600, 0.090, 1.200);
        double warmEnd = simulate(6.0, midnight, false, warm, 60, 1.0 / 60);
        double coolEnd = simulate(6.0, midnight, false, cool, 60, 1.0 / 60);
        assertTrue(warmEnd<coolEnd,"高温应掉得更快：32℃="+warmEnd+"18℃="+coolEnd);
     }
    @Test
    @DisplayName("调大产氧系数，白天升得更快")
    void higherProduceCoefficientRaisesDoFaster(){
         LocalDateTime noon = LocalDateTime.of(2026,1,1,12,0);
        double slow = simulate(4.0, noon, false, withRates(0.300, 0.090), 30, 1.0 / 60);
        double fast = simulate(4.0, noon, false, withRates(1.200, 0.090), 30, 1.0 / 60);
        assertTrue(fast>slow,
                "产氧系数大的应升得更快：大="+fast+"小="+slow);
    }
    @Test
    @DisplayName("调大耗氧系数，夜间掉得更快")
    void higherConsumeCoefficientDropsDoFaster(){
         LocalDateTime midnight = LocalDateTime.of(2026,1,1,0,0);
        double slow = simulate(8.0, midnight, false, withRates(0.600, 0.030), 60, 1.0 / 60);
        double fast = simulate(8.0, midnight, false, withRates(0.600, 0.300), 60, 1.0 / 60);
        assertTrue(fast<slow,
                "好氧系数大的应该掉的更快：大="+fast+"小="+slow);
    }

    @Test
    @DisplayName("溶氧永远不会变成负数")
    void doNeverGoesNegative(){
        double end = simulate(0.5, LocalDateTime.of(2026, 1, 1, 0, 0),
                false, withRates(0.0, 5.0), 600, 1.0 / 60);
        assertTrue(end>=0.0,"溶氧不能为负，实际 "+end);
    }
    @Test
    @DisplayName("超饱和时会回落到饱和值附近，不会无限增长")
    void  supersaturatedWaterFallsBackToSaturation(){
        // 故意给一个远超饱和值的初值
        double end = simulate(20.0, LocalDateTime.of(2026, 1, 1, 0, 0),
                false, fixedParams(), 600, 1.0 / 60);
        double saturation = Physics.saturationDo(25.0);
        assertTrue(end<saturation*1.2,
                "超饱和应回落，饱和值="+saturation+"实际="+end);
    }
    @Test
    @DisplayName("日内曲线模式下，水温随时刻变化")
    void diurnalTemperatureVariesWithinDay(){
        SimParams diurnal = SimParams.defaults();
        double at1500 = Physics.waterTemperature(LocalDateTime.of(2026, 1, 1, 15, 0), diurnal);
        double at0300 = Physics.waterTemperature(LocalDateTime.of(2026, 1, 1, 3, 0), diurnal);
        assertTrue(at1500>at0300,"下午3点应该比凌晨3点热");
        assertEquals(27.0,at1500,1e-6); // 25 +2×sin(0) = 27
        assertEquals(23.0,at0300,1e-6);
    }

}

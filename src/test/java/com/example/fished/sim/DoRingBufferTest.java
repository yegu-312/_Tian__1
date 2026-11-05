package com.example.fished.sim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DoRingBufferTest {
    private static DoPoint p(long minute,double doValue){
        return new DoPoint(minute,doValue,25.0,false);
    }
  @Test
  @DisplayName("满了以后抛弃最旧的，容易恒定")
  void dropsOldestWhenFull(){
      DoRingBuffer buf = new DoRingBuffer(3);
      for (int i = 0; i < 5; i++) {
          buf.sampleIfDue(p(i*10,i),10);
      }
      assertEquals(3,buf.size(),"容量上限是 3");
      List<DoPoint> all = buf.latest(10);
      assertEquals(3, all.size());
      assertEquals(20,all.get(0).minuteOffset(),"最旧的是第 3 个点：i=2，分钟 20");
      assertEquals(40, all.get(2).minuteOffset(), "最新的是第 5 个点：i=4，分钟 40");

  }
  @Test
  @DisplayName("不到采样间隔就不采")
  void doesNotSampleBeforeInterval() {
      DoRingBuffer buf = new DoRingBuffer(100);

      assertTrue(buf.sampleIfDue(p(0, 6.0), 60), "第一个点总是采");
      assertFalse(buf.sampleIfDue(p(30, 6.1), 60), "才过 30 分钟，不采");
      assertFalse(buf.sampleIfDue(p(59, 6.2), 60), "才过 59 分钟，不采");
      assertTrue(buf.sampleIfDue(p(60, 6.3), 60), "满 60 分钟了，采");
      assertTrue(buf.sampleIfDue(p(120, 6.4), 60), "又满 60 分钟，采");

      assertEquals(3, buf.size());
  }

    @Test
    @DisplayName("latest 按时间正序返回，limit超量时返回全部")
    void latestReturnsInChronologicalOrder() {
        DoRingBuffer buf = new DoRingBuffer(100);
        for (int i = 1; i <= 5; i++) {
            buf.sampleIfDue(p(i * 10, i), 10);
        }

        List<DoPoint> recent = buf.latest(2);
        assertEquals(2, recent.size());
        assertEquals(40, recent.get(0).minuteOffset(), "先返回较早的：i=4，分钟 40");
        assertEquals(50, recent.get(1).minuteOffset(), "再返回较晚的的：i=5，分钟 50");

        assertEquals(5, buf.latest(999).size(), "limit 超量时返回全部");
    }


    @Test
    @DisplayName("clear后数据清空，且采样计时也重新开始")
    void clearResetsEverything() {
        DoRingBuffer buf = new DoRingBuffer(100);
        buf.sampleIfDue(p(0, 6.0), 60);
        buf.sampleIfDue(p(60, 6.1), 60);
        buf.clear();
        assertEquals(0, buf.size());
        assertTrue(buf.sampleIfDue(p(0, 6.5), 60), "清空后即使分钟数没到间隔，也应该重新开始采");
    }
}

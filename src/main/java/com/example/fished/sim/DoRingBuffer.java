package com.example.fished.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
//存储溶氧历史数据点
public class DoRingBuffer {
    private final int capacity;// 最大容量
    private final ArrayDeque<DoPoint> deque; // 双端队列，存历史点

    private long lastSampleMinute=Long.MAX_VALUE;// 上次采样的分钟偏移量

    public DoRingBuffer(int capacity) {
        if(capacity<=0){
            throw new IllegalArgumentException("容量必须大于0");
        }
        this.capacity = capacity;
        this.deque=new ArrayDeque<>(capacity);
    }
    //按间隔采样
    public boolean sampleIfDue(DoPoint point,int intervalMinutes){
        if(lastSampleMinute!=Long.MAX_VALUE&&point.minuteOffset()-lastSampleMinute<intervalMinutes){
            return false;
        }
        // 满了就淘汰最旧的
        if(deque.size()>=capacity){
            deque.pollFirst();
        }
        deque.addLast(point);
        lastSampleMinute=point.minuteOffset();
        return true;
    }
    //取最近 N 条数据，返回从旧到新的列表
    public List<DoPoint> latest(int limit){
        List<DoPoint> all=new ArrayList<>(deque); // 拷贝一份
        int from=Math.max(0,all.size()-limit); // 计算起始位置
        return new ArrayList<>(all.subList(from,all.size()));
    }
    //清空缓冲区，重置采样状态
    public void clear(){
        deque.clear();
        lastSampleMinute=Long.MAX_VALUE;
    }
    //当前存了多少条
    public int size(){
        return deque.size();
    }
    //最大容量
    public int capacity(){
        return capacity;
    }
}

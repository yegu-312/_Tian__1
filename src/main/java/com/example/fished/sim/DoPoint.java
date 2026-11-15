package com.example.fished.sim;
//曲线上一个点
public record DoPoint(long minuteOffset, double doValue,
                      double temperature, boolean aeratorOn,
                      double ph) {
    /** 兼容旧调用：未提供 pH 时用默认值。 */
    public DoPoint(long minuteOffset, double doValue, double temperature, boolean aeratorOn) {
        this(minuteOffset, doValue, temperature, aeratorOn, Physics.DEFAULT_PH);
    }
}

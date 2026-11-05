package com.example.fished.sim;
//曲线上一个点
public record DoPoint(long minuteOffset, double doValue,
                      double temperature, boolean aeratorOn) {
}

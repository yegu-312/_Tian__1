package com.example.fished.config;

/**
 * 仿真引擎不再由单例 Bean 提供，改由
 * {@link com.example.fished.sim.PondManager} 按鱼塘创建和维护。
 */
public final class SimConfig {
    private SimConfig() {
    }
}

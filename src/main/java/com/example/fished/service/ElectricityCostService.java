package com.example.fished.service;

import com.example.fished.vo.ElectricityCostVO;

public interface ElectricityCostService {

    /**
     * 统计指定鱼塘今天、最近 7 天、最近 30 天的增氧机运行时长、耗电量与分时电费。
     * pondId 为空时使用当前第一个鱼塘。
     */
    ElectricityCostVO getCost(Long pondId);
}

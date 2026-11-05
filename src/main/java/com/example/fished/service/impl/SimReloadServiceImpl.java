package com.example.fished.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.fished.entity.AeratorStrategy;
import com.example.fished.entity.PondConfig;
import com.example.fished.entity.SimSetting;
import com.example.fished.mapper.AeratorStrategyMapper;
import com.example.fished.mapper.PondConfigMapper;
import com.example.fished.mapper.SimSettingMapper;
import com.example.fished.service.SimReloadService;
import com.example.fished.sim.PondManager;
import com.example.fished.sim.SimAssembler;
import com.example.fished.sim.SimParams;
import com.example.fished.sim.StrategyParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
//仿真引擎的"重启按钮"服务
@Service
@Slf4j
public class SimReloadServiceImpl implements SimReloadService {

    private final PondConfigMapper pondConfigMapper;
    private final AeratorStrategyMapper strategyMapper;
    private final SimSettingMapper settingMapper;
    private final PondManager pondManager;

    public SimReloadServiceImpl(PondConfigMapper pondConfigMapper,
                                AeratorStrategyMapper strategyMapper,
                                SimSettingMapper settingMapper,
                                PondManager pondManager) {
        this.strategyMapper = strategyMapper;
        this.settingMapper = settingMapper;
        this.pondManager = pondManager;
        this.pondConfigMapper = pondConfigMapper;
    }

    @Override
    public void reload(Long pondId) {
        PondConfig pondConfig = pondConfigMapper.selectById(pondId);// 水体参数
        AeratorStrategy strategy = strategyMapper.selectOne(
                new LambdaQueryWrapper<AeratorStrategy>()
                        .eq(AeratorStrategy::getPondId, pondId)
                        .orderByAsc(AeratorStrategy::getId)
                        .last("LIMIT 1")); // 增氧策略
        SimSetting setting = settingMapper.selectOne(
                new LambdaQueryWrapper<SimSetting>()
                        .eq(SimSetting::getPondId, pondId)
                        .orderByAsc(SimSetting::getId)
                        .last("LIMIT 1")); // 仿真设置
        SimParams simParams = SimAssembler.toSimParams(pondConfig); // 水体物理参数
        StrategyParams strategyParams = SimAssembler.toStrategyParams(strategy, setting);// 策略参数
        //提取仿真步长和采样间隔
        int stepSeconds = setting == null || setting.getStepSeconds() == null
                ? 60 : setting.getStepSeconds();
        int sampleIntervalSec = setting == null || setting.getSampleIntervalSec() == null
                ? 60 : setting.getSampleIntervalSec();
        //通知对应鱼塘的引擎热更新
        pondManager.requireEngine(pondId)
                .updateConfig(simParams, strategyParams, stepSeconds, sampleIntervalSec);

        log.info("鱼塘 #{} 模拟参数已热更新：步长={}秒，水体参数={}", pondId, stepSeconds, simParams);
    }
}

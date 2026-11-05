package com.example.fished.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.fished.common.BusinessException;
import com.example.fished.common.DiffBuilder;
import com.example.fished.common.enums.OperationActionEnum;
import com.example.fished.common.enums.OperationModuleEnum;
import com.example.fished.dto.SettingUpdateDTO;
import com.example.fished.entity.SimSetting;
import com.example.fished.mapper.SimSettingMapper;
import com.example.fished.service.OperationLogService;
import com.example.fished.service.SimReloadService;
import com.example.fished.service.SimSettingService;
import com.example.fished.sim.PondManager;
import com.example.fished.sim.PondSnapshot;
import com.example.fished.vo.SettingVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class SimSettingServiceImpl extends ServiceImpl<SimSettingMapper, SimSetting> implements SimSettingService {

    private final PondManager pondManager;
    private final SimReloadService simReloadService;
    private final OperationLogService operationLogService;

    public SimSettingServiceImpl(PondManager pondManager,
                                 SimReloadService simReloadService,OperationLogService operationLogService) {
        this.pondManager = pondManager;
        this.simReloadService = simReloadService;
        this.operationLogService = operationLogService;
    }
    //读取设置配置
    @Override
    public SettingVO getSetting(Long pondId) {
        return SettingVO.from(requireSetting(pondId));
    }

    @Override
    public SettingVO updateSetting(Long pondId, SettingUpdateDTO dto, String operator) {
        SimSetting entity = requireSetting(pondId);
        SimSetting before = new SimSetting();
        BeanUtils.copyProperties(entity, before);
        entity.setSafetyVetoEnabled(dto.getSafetyVetoEnabled());//安全否决开不开
        entity.setStepSeconds(dto.getStepSeconds());//模拟步长
        entity.setSampleIntervalSec(dto.getSampleIntervalSec());//采样间隔
        updateById(entity);
        simReloadService.reload(pondId);
        operationLogService.record(OperationModuleEnum.SETTING, OperationActionEnum.UPDATE,
                "鱼塘#" + pondId + " " + diff(before, entity), operator);
        return SettingVO.from(entity);
    }
    //重置模拟
    @Override
    public PondSnapshot reset(Long pondId, String operator) {
        PondSnapshot snapshot = pondManager.requireEngine(pondId).reset();
        operationLogService.record(OperationModuleEnum.SETTING, OperationActionEnum.RESET,
                String.format("鱼塘#%d 重置到 %s，溶氧 %.2f，倍速 1，历史清空", pondId, snapshot.pondTime(),
                        snapshot.currentDo()), operator);
        return snapshot;
    }
    //差异文本
    private static String diff(SimSetting before, SimSetting after) {
        return new DiffBuilder()
                .appendIfChanged("安全否决",
                        before.getSafetyVetoEnabled(),
                        after.getSafetyVetoEnabled())
                .appendIfChanged("模拟步长",
                        before.getStepSeconds() +
                                "秒", after.getStepSeconds() + "秒")
                .appendIfChanged("采样间隔",
                        before.getSampleIntervalSec()
                                + "秒", after.getSampleIntervalSec() + "秒")
                .text();
    }

    //取设置（按 pond_id 而不是主键 id）
    private SimSetting requireSetting(Long pondId) {
        SimSetting entity = getOne(new LambdaQueryWrapper<SimSetting>()
                .eq(SimSetting::getPondId, pondId)
                .orderByAsc(SimSetting::getId)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new BusinessException("鱼塘 #" + pondId + " 的系统设置不存在");
        }
        return entity;
    }
}

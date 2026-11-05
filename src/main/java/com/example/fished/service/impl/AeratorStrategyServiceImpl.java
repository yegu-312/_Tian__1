package com.example.fished.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.fished.common.BusinessException;
import com.example.fished.common.DiffBuilder;
import com.example.fished.common.enums.AeratorModeEnum;
import com.example.fished.common.enums.OperationActionEnum;
import com.example.fished.common.enums.OperationModuleEnum;
import com.example.fished.dto.StrategyUpdateDTO;
import com.example.fished.entity.AeratorStrategy;
import com.example.fished.entity.SimSetting;
import com.example.fished.mapper.AeratorStrategyMapper;
import com.example.fished.mapper.SimSettingMapper;
import com.example.fished.service.AeratorStrategyService;
import com.example.fished.service.OperationLogService;
import com.example.fished.service.SimReloadService;
import com.example.fished.sim.StrategyParams;
import com.example.fished.vo.StrategyVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;


@Slf4j
@Service//告诉spring:这是一个Service组件,要注册成Bean
public class AeratorStrategyServiceImpl extends ServiceImpl<AeratorStrategyMapper, AeratorStrategy> implements AeratorStrategyService {

    private final SimSettingMapper settingMapper;
    //重新加载参数
    private final SimReloadService simReloadService;
    private final OperationLogService operationLogService;

    public AeratorStrategyServiceImpl(SimSettingMapper settingMapper, SimReloadService simReloadService,OperationLogService operationLogService) {
        this.settingMapper = settingMapper;
        this.simReloadService = simReloadService;
        this.operationLogService = operationLogService;
    }

    @Override
    public StrategyVO getStrategy(Long pondId) {
        return StrategyVO.from(requireStrategy(pondId));
    }
  //更新策略
    @Override
    public StrategyVO updateStrategy(Long pondId, StrategyUpdateDTO dto, String operator) {
        AeratorStrategy entity = requireStrategy(pondId);

        StrategyParams checked = new StrategyParams(
                dto.getMode(), dto.getOnThreshold().doubleValue(),
                dto.getOffThreshold().doubleValue(), dto.getSafetyFloor().doubleValue(),
                dto.getPredictMinutes(), dto.getManualOn(), currentSafetyVetoEnable(pondId));
        AeratorStrategy before = new AeratorStrategy();
        BeanUtils.copyProperties(entity, before);
        entity.setMode(dto.getMode());
        entity.setOnThreshold(dto.getOnThreshold());
        entity.setOffThreshold(dto.getOffThreshold());
        entity.setSafetyFloor(dto.getSafetyFloor());
        entity.setPredictMinutes(dto.getPredictMinutes());
        entity.setManualOn(dto.getManualOn());
        updateById(entity);

        simReloadService.reload(pondId);//通知对应鱼塘的数据引擎重读一遍
        operationLogService.record(OperationModuleEnum.AERATOR,
                OperationActionEnum.UPDATE,
                "鱼塘#" + pondId + " " + diff(before, entity), operator);
        return StrategyVO.from(entity);
    }
  //手动开关
    @Override
    public StrategyVO switchManual(Long pondId, boolean on, String operator) {
        AeratorStrategy entity = requireStrategy(pondId);
        entity.setMode(AeratorModeEnum.MANUAL.getCode());//模式切换为手动
        entity.setManualOn(on);
        updateById(entity);
        simReloadService.reload(pondId);
        operationLogService.record(OperationModuleEnum.AERATOR,
                on ? OperationActionEnum.MANUAL_ON : OperationActionEnum.MANUAL_OFF,
                "鱼塘#" + pondId + " " + (on ? "人工开启增氧机（已切到手动模式）" : "人工关闭增氧机（已切到手动模式）"), operator);
        return StrategyVO.from(entity);
    }
    //生成变更日志,记录改变的情况
    private static String diff(AeratorStrategy before,AeratorStrategy after){
        return new DiffBuilder()
                .appendIfChanged("控制模式",
                        before.getMode(), after.getMode())
                .appendIfChanged("开机阈值",
                        before.getOnThreshold(), after.getOnThreshold())
                .appendIfChanged("关机阈值",
                        before.getOffThreshold(), after.getOffThreshold())
                .appendIfChanged("安全下限",
                        before.getSafetyFloor(), after.getSafetyFloor())
                .appendIfChanged("提前开机",
                        before.getPredictMinutes() +
                                "分钟", after.getPredictMinutes() + "分钟")
                .appendIfChanged("手动开关",
                        before.getManualOn(), after.getManualOn())
                .text();
    }
    //查询增氧策略是否存在（按 pond_id 而不是主键 id）
    private AeratorStrategy requireStrategy(Long pondId) {
        AeratorStrategy entity = getOne(new LambdaQueryWrapper<AeratorStrategy>()
                .eq(AeratorStrategy::getPondId, pondId)
                .orderByAsc(AeratorStrategy::getId)
                .last("LIMIT 1"));
        if(entity==null){
            throw new BusinessException("鱼塘 #" + pondId + " 的增氧策略不存在");
        }
        return entity;
    }
     //判断是否启用安全否决功能,用于安全保护
    private boolean currentSafetyVetoEnable(Long pondId){
        SimSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<SimSetting>()
                .eq(SimSetting::getPondId, pondId)
                .orderByAsc(SimSetting::getId)
                .last("LIMIT 1"));
        if(setting==null||setting.getSafetyVetoEnabled()==null){
            return true;
        }
        return setting.getSafetyVetoEnabled();
    }
}

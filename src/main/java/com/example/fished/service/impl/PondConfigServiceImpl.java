package com.example.fished.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.fished.common.BusinessException;
import com.example.fished.common.DiffBuilder;
import com.example.fished.common.enums.OperationActionEnum;
import com.example.fished.common.enums.OperationModuleEnum;
import com.example.fished.dto.PondConfigUpdateDTO;
import com.example.fished.entity.PondConfig;
import com.example.fished.mapper.PondConfigMapper;
import com.example.fished.service.OperationLogService;
import com.example.fished.service.PondConfigService;
import com.example.fished.service.SimReloadService;
import com.example.fished.vo.PondConfigVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;


@Service
@Slf4j
//鱼塘配置管理的服务实现类
public class PondConfigServiceImpl extends ServiceImpl<PondConfigMapper, PondConfig> implements PondConfigService {
   private final SimReloadService simReloadService;
   private final OperationLogService operationLogService;

   public PondConfigServiceImpl(SimReloadService simReloadService,OperationLogService operationLogService) {
       this.simReloadService = simReloadService;
       this.operationLogService = operationLogService;
   }
    @Override
    public PondConfigVO getConfig(Long pondId) {
        return PondConfigVO.from(requireConfig(pondId));
    }

    @Override
    public PondConfigVO updateConfig(Long pondId, PondConfigUpdateDTO dto, String operator)
    {
        PondConfig entity = requireConfig(pondId);
        PondConfig before = new PondConfig();
        BeanUtils.copyProperties(entity, before);

        entity.setPondName(dto.getPondName());
        entity.setTemperatureMode(dto.getTemperatureMode());
        entity.setBaseTemperature(dto.getBaseTemperature());
        entity.setDailyAmplitude(dto.getDailyAmplitude());
        entity.setDoMin(dto.getDoMin());
        entity.setDoMax(dto.getDoMax());
        entity.setProduceCoefficient(dto.getProduceCoefficient());
        entity.setConsumeCoefficient(dto.getConsumeCoefficient());
        entity.setAeratorRate(dto.getAeratorRate());
        updateById(entity);
        // //通知对应鱼塘的仿真引擎重新加载
        simReloadService.reload(pondId);

        operationLogService.record(OperationModuleEnum.POND_CONFIG, OperationActionEnum.UPDATE,
                "鱼塘#" + pondId + " " + diff(before, entity), operator);

        return PondConfigVO.from(entity);
    }
    //记录操作日志（对比差异记日志）
    private static String diff(PondConfig before,
                               PondConfig after) {
        return new DiffBuilder()
                .appendIfChanged("鱼塘名称",
                        before.getPondName(), after.getPondName())
                .appendIfChanged("温度模式",
                        before.getTemperatureMode(),
                        after.getTemperatureMode())
                .appendIfChanged("基准温度",
                        before.getBaseTemperature(),
                        after.getBaseTemperature())
                .appendIfChanged("昼夜振幅",
                        before.getDailyAmplitude(), after.getDailyAmplitude())
                .appendIfChanged("溶氧下限",
                        before.getDoMin(), after.getDoMin())
                .appendIfChanged("溶氧上限",
                        before.getDoMax(), after.getDoMax())
                .appendIfChanged("产氧系数",
                        before.getProduceCoefficient(),
                        after.getProduceCoefficient())
                .appendIfChanged("耗氧系数",
                        before.getConsumeCoefficient(),
                        after.getConsumeCoefficient())
                .appendIfChanged("增氧速率",
                        before.getAeratorRate(), after.getAeratorRate())
                .text();
   }
   // 强制获取配置
    private PondConfig requireConfig(Long pondId) {
        PondConfig entity = getById(pondId);
        if(entity==null){
            throw new BusinessException( "鱼塘配置不存在：#" + pondId);
        }
        return entity;
    }


}

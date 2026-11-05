package com.example.fished.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.fished.common.PageResult;
import com.example.fished.common.enums.AeratorActionEnum;
import com.example.fished.dto.AeratorLogQueryDTO;
import com.example.fished.entity.AeratorLog;
import com.example.fished.mapper.AeratorLogMapper;
import com.example.fished.service.AeratorLogService;
import com.example.fished.sim.event.AeratorStateChangedEvent;
import com.example.fished.vo.AeratorLogVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
//记录增氧机情况
@Service
public class AeratorLogServiceImpl  extends ServiceImpl<AeratorLogMapper, AeratorLog>implements AeratorLogService {
    //记录增氧事件
    @Override
    public void record(AeratorStateChangedEvent event){
        AeratorLog entity = new AeratorLog();
        entity.setPondId(event.pondId());
        entity.setAction(AeratorActionEnum.of(event.on()).getCode());
        entity.setTriggerReason(truncate(event.reason(),128));//改变原因
        entity.setDoValue(BigDecimal.valueOf(round2(event.doValue())));//溶氧值
        entity.setPondTime(event.pondTime());
        entity.setOperator(event.operator());
        save(entity);
    }

    @Override
    public PageResult<AeratorLogVO> pageQuery(AeratorLogQueryDTO dto) {
        LambdaQueryWrapper<AeratorLog> wrapper = new LambdaQueryWrapper<AeratorLog>()
                .eq(dto.getPondId() != null, AeratorLog::getPondId, dto.getPondId())
                .eq(StringUtils.hasText(dto.getAction()),
                        AeratorLog::getAction, dto.getAction())
                .ge(dto.getStartTime() != null,
                        AeratorLog::getPondTime, dto.getStartTime())
                .le(dto.getEndTime() != null,
                        AeratorLog::getPondTime, dto.getEndTime())
                  .orderByDesc(AeratorLog::getPondTime)
                .orderByDesc(AeratorLog::getId);

        Page<AeratorLog> page = page(new Page<>(dto.getPage(), dto.getSize()), wrapper);
        List<AeratorLogVO> vos = page.getRecords().stream().map(AeratorLogVO::from).toList();
        return PageResult.of(page, vos);
    }

    private static String truncate(String s,int max){
        if (s == null) {
            return null;
        }
        return s.length()<=max?s:s.substring(0,max);
    }
    //取小数为2
    private static double round2(double v){
        return Math.round(v*100.0)/100.0;
    }
}

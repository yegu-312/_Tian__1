package com.example.fished.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.fished.common.PageResult;
import com.example.fished.common.enums.OperationActionEnum;
import com.example.fished.common.enums.OperationModuleEnum;
import com.example.fished.dto.OperationLogQueryDTO;
import com.example.fished.entity.OperationLog;
import com.example.fished.mapper.OperationLogMapper;
import com.example.fished.service.OperationLogService;
import com.example.fished.sim.PondManager;
import com.example.fished.vo.OperationLogVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {
    private final PondManager pondManager;

    public OperationLogServiceImpl(PondManager pondManager){
        this.pondManager = pondManager;
    }
    //记录日志
    @Override
    public void record(OperationModuleEnum module, OperationActionEnum action, String detail, String operator) {
        try{
            OperationLog entity = new OperationLog();
            entity.setModule(module.getCode());
            entity.setAction(action.getCode());
            entity.setDetail(truncate(detail,512));
            entity.setOperator(operator);
            entity.setPondTime(referencePondTime());
            save(entity);
        }catch (Exception e){
            log.error("写操作日志失败：module={}, action={}, detail={}", module, action, detail, e);
        }
    }
    //分页查询日志
    @Override
    public PageResult<OperationLogVO> pageQuery(OperationLogQueryDTO dto) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .eq(StringUtils.hasText(dto.getModule()),
                        OperationLog::getModule, dto.getModule())//按模块筛选
                .eq(StringUtils.hasText(dto.getOperator()),
                        OperationLog::getOperator, dto.getOperator())
                .orderByDesc(OperationLog::getCreateTime)
                .orderByDesc(OperationLog::getId);

        Page<OperationLog> page = page(new Page<>(dto.getPage(), dto.getSize()), wrapper);

        List<OperationLogVO> vos = page.getRecords().stream()
                        .map(OperationLogVO::from).toList();

        return PageResult.of(page, vos);  // 返回分页元信息 + 翻译后的记录
    }
    /** 操作日志是全局列表，鱼塘时间以第一个鱼塘为参考；详情中会带上鱼塘编号。 */
    private java.time.LocalDateTime referencePondTime() {
        List<Long> ids = pondManager.pondIds();
        return ids.isEmpty()
                ? java.time.LocalDateTime.now()
                : pondManager.requireEngine(ids.get(0)).getSnapshot().pondTime();
    }
    //字符串截断防止过长
    private static String truncate(String s,int max){
        if(s==null){
            return null;
        }
        return s.length()<=max?s: s.substring(0, max);
    }
}

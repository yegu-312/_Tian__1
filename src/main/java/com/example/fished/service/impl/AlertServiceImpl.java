package com.example.fished.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.fished.common.BusinessException;
import com.example.fished.common.PageResult;
import com.example.fished.common.enums.AlertStatusEnum;
import com.example.fished.common.enums.OperationActionEnum;
import com.example.fished.common.enums.OperationModuleEnum;
import com.example.fished.dto.AlertQueryDTO;
import com.example.fished.entity.Alert;
import com.example.fished.mapper.AlertMapper;
import com.example.fished.service.AlertService;
import com.example.fished.service.OperationLogService;
import com.example.fished.sim.event.AlertRaisedEvent;
import com.example.fished.vo.AlertVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertServiceImpl extends ServiceImpl<AlertMapper, Alert>implements AlertService {
    private final OperationLogService operationLogService;

    public AlertServiceImpl(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }
    //产生警告
    @Override
    public void raise(AlertRaisedEvent event) {
        Alert entity = new Alert();

        entity.setPondId(event.pondId());
        entity.setAlertType(event.alertType().getCode());
        entity.setLevel(event.level().getCode());

        entity.setStatus(AlertStatusEnum.PENDING.getCode());
        entity.setMessage(truncate(event.message(), 255));
        // 溶氧告警记录溶氧值；水温辅助告警不携带溶氧值，具体水温写在 message 中。
        if (event.doValue() != null) {
            entity.setDoValue(BigDecimal.valueOf(round2(event.doValue())));
        }
        entity.setPondTime(event.pondTime());//鱼塘时间
        save(entity);
    }
    //查询警告记录
    @Override
    public PageResult<AlertVO> pageQuery(AlertQueryDTO dto) {
        LambdaQueryWrapper<Alert> wrapper=new LambdaQueryWrapper<Alert>()
                .eq(dto.getPondId() != null, Alert::getPondId, dto.getPondId())
                .eq(StringUtils.hasText(dto.getLevel()),Alert::getLevel,dto.getLevel())
                .eq(StringUtils.hasText(dto.getStatus()),Alert::getStatus,dto.getStatus())
                .eq(StringUtils.hasText(dto.getAlertType()),Alert::getAlertType,dto.getAlertType())
                .orderByDesc(Alert::getPondTime)
                .orderByDesc(Alert::getId);

        Page<Alert> page = page(new Page<>(dto.getPage(), dto.getSize()), wrapper);

        List<AlertVO> vos = page.getRecords().stream().map(AlertVO::from).toList();
        return PageResult.of(page,vos);
    }
    //确认警告情况
    @Override
    public void ack(Long id, String operator) {
        Alert alert = getById(id);
        if(alert==null){
            throw new BusinessException("告警不存在："+id);
        }
        if(AlertStatusEnum.ACKNOWLEDGED.getCode().equals(alert.getStatus())){
            throw new BusinessException("这条告警已经被"+alert.getAckBy()+"确认过了，无需重复确认");
        }
        alert.setStatus(AlertStatusEnum.ACKNOWLEDGED.getCode());
        alert.setAckTime(LocalDateTime.now());
        alert.setAckBy(operator);
        updateById(alert);

        operationLogService.record(OperationModuleEnum.ALERT, OperationActionEnum.ACK,
                "鱼塘#" + alert.getPondId() + " 确认告警 #" + id + "（" + alert.getAlertType() + "，"
                        + alert.getLevel() + "级，" + alert.getMessage() + "）", operator);
    }
    //字符串剪切，限制字符串最大长度
    private static String truncate(String s, int max)
    {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
    //保留两位小数
    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

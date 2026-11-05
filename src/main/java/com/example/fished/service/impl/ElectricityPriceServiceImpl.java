package com.example.fished.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.fished.common.enums.ElectricityPeriodEnum;
import com.example.fished.common.enums.OperationActionEnum;
import com.example.fished.common.enums.OperationModuleEnum;
import com.example.fished.dto.ElectricityPeriodDTO;
import com.example.fished.dto.ElectricityUpdateDTO;
import com.example.fished.entity.ElectricityPrice;
import com.example.fished.mapper.ElectricityPriceMapper;
import com.example.fished.service.ElectricityPriceService;
import com.example.fished.service.OperationLogService;
import com.example.fished.sim.PondManager;
import com.example.fished.vo.ElectricityVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ElectricityPriceServiceImpl extends ServiceImpl<ElectricityPriceMapper,ElectricityPrice> implements ElectricityPriceService {

    private static final long POND_ID=1L;
    private final PondManager pondManager;
    private final OperationLogService operationLogService;

    public ElectricityPriceServiceImpl(PondManager pondManager, OperationLogService operationLogService) {
        this.pondManager = pondManager;
        this.operationLogService = operationLogService;
    }
    //从数据库读电价配置，组装成 VO 返回
    @Override
    public ElectricityVO getConfig() {
        return buildVO(loadPeriods());
    }
    //更新记录配置
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ElectricityVO updateConfig(ElectricityUpdateDTO dto, String operator) {
        ElectricityUpdateDTO.checkWholeDay(dto.getPeriods());
        List<ElectricityPrice> old=loadPeriods();//读备份旧配置
        remove(new LambdaQueryWrapper<ElectricityPrice>().eq(ElectricityPrice::getPondId,POND_ID));
        List<ElectricityPrice> rows=dto.getPeriods().stream().
                map(ElectricityPriceServiceImpl::toEntity).toList();//写入新配置
        saveBatch(rows);//批量插入
        operationLogService.record(OperationModuleEnum.PRICE, OperationActionEnum.UPDATE,
                old.size() + " 段 → " + rows.size() + " 段：" + describe(rows), operator);
        return buildVO(rows);
    }
    //读取鱼塘分时电价段
    private List<ElectricityPrice> loadPeriods() {
        List<ElectricityPrice> rows = list(new
                LambdaQueryWrapper<ElectricityPrice>()
                .eq(ElectricityPrice::getPondId, POND_ID)
                .orderByAsc(ElectricityPrice::getStartHour));
        return rows.isEmpty() ? defaults() : rows;
    }
    //把数据库里的电价段组装成前端需要的视图对象
    private ElectricityVO buildVO(List<ElectricityPrice> rows) {
        List<ElectricityVO.Period> periods =
                rows.stream()
                        .sorted(Comparator.comparingInt(ElectricityPrice::getStartHour))
                        .map(r -> new ElectricityVO.Period(
                                r.getPeriodType(),
                                ElectricityPeriodEnum.getByCode(r.getPeriodType())
                                        .map(ElectricityPeriodEnum::getDesc).orElse(r.getPeriodType()),
                                r.getStartHour(),
                                r.getEndHour(), r.getPrice()))
                        .toList();
        BigDecimal[] hourly = new BigDecimal[24];
        //把"分时电价段"展开成一个长度为 24 的小时价格数组
        for (ElectricityVO.Period p : periods) {
            for (int h = p.startHour(); h < p.endHour(); h++) {
                hourly[h] = p.price();
            }
        }
        LocalDateTime pondTime = referencePondTime();
        int hour = pondTime.getHour();
        //找出"当前小时落在哪个时段里"
        ElectricityVO.Period nowPeriod =
                periods.stream()
                        .filter(p -> hour >= p.startHour() && hour < p.endHour())
                        .findFirst()
                        .orElse(null);
        ElectricityVO vo = new ElectricityVO();
        vo.setPeriods(periods);
        vo.setHourlyPrices(Arrays.asList(hourly));
        vo.setCurrent(nowPeriod == null ? null : new ElectricityVO.Current(
                nowPeriod.periodType(),
                nowPeriod.periodDesc(),
                nowPeriod.price(), pondTime));
        return vo;
    }
    /** 电价是全局配置，时段色带用第一个在运行鱼塘的时间作为参考。 */
    private LocalDateTime referencePondTime() {
        List<Long> ids = pondManager.pondIds();
        return ids.isEmpty()
                ? LocalDateTime.now()
                : pondManager.requireEngine(ids.get(0)).getSnapshot().pondTime();
    }
    //把前端传来的数据传输对象转换成数据库实体对象
    private static ElectricityPrice toEntity(ElectricityPeriodDTO p) {
        ElectricityPrice e = new ElectricityPrice();
        e.setPondId(POND_ID);
        e.setPeriodType(p.getPeriodType());
        e.setStartHour(p.getStartHour());
        e.setEndHour(p.getEndHour());
        e.setPrice(p.getPrice());
        return e;
    }
    //把一堆电价配置段拼成一句可以阅读的话
    private static String describe(List<ElectricityPrice> rows) {
        return rows.stream()
                .sorted(Comparator.comparingInt(ElectricityPrice::getStartHour))
                .map(r -> String.format("%s%d-%d@%s",//// 格式化每一段
                                 ElectricityPeriodEnum.getByCode(r.getPeriodType())
                                .map(ElectricityPeriodEnum::getDesc).orElse(r.getPeriodType()),//峰/平/谷的描述
                        r.getStartHour(),
                        r.getEndHour(), r.getPrice()))
                .collect(Collectors.joining("，"));
    }
    //默认电价配置
    private static List<ElectricityPrice> defaults() {
        return List.of(
                row("valley",  0,  7, "0.30"),
                row("flat",    7, 10, "0.70"),
                row("peak",   10, 15, "1.10"),
                row("flat",   15, 18, "0.70"),
                row("peak",   18, 21, "1.10"),
                row("flat",   21, 23, "0.70"),
                row("valley", 23, 24, "0.30"));
    }
    //快速创建一行电价
    private static ElectricityPrice row(String type, int start, int end, String price) {
        ElectricityPrice e = new ElectricityPrice();
        e.setPondId(POND_ID);
        e.setPeriodType(type);
        e.setStartHour(start);
        e.setEndHour(end);
        e.setPrice(new BigDecimal(price));
        return e;
    }
}

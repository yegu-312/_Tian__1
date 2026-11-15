package com.example.fished.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.fished.common.BusinessException;
import com.example.fished.dto.HistoryQueryDTO;
import com.example.fished.entity.DoSample;
import com.example.fished.mapper.DoSampleMapper;
import com.example.fished.service.HistoryService;
import com.example.fished.sim.PondManager;
import com.example.fished.sim.SimulationEngine;
import com.example.fished.sim.StrategyParams;
import com.example.fished.vo.HistorySeriesVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class HistoryServiceImpl implements HistoryService {

    private static final DateTimeFormatter INPUT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final PondManager pondManager;
    private final DoSampleMapper doSampleMapper;

    @Value("${app.history.retention-days:31}")
    private int retentionDays;

    public HistoryServiceImpl(PondManager pondManager, DoSampleMapper doSampleMapper) {
        this.pondManager = pondManager;
        this.doSampleMapper = doSampleMapper;
    }

    @Override
    public HistorySeriesVO series(HistoryQueryDTO query) {
        Resolved resolved = resolve(query);
        SimulationEngine engine = pondManager.requireEngine(resolved.pondId);
        StrategyParams strategy = engine.getStrategyParams();

        List<DoSample> rows = doSampleMapper.selectList(new LambdaQueryWrapper<DoSample>()
                .eq(DoSample::getPondId, resolved.pondId)
                .ge(DoSample::getPondTime, resolved.from)
                .le(DoSample::getPondTime, resolved.to)
                .orderByAsc(DoSample::getPondTime)
                .orderByAsc(DoSample::getId));

        HistoryAggregator.Result result = HistoryAggregator.aggregate(
                rows, resolved.from, resolved.to,
                strategy.onThreshold(), strategy.offThreshold(), strategy.safetyFloor());

        HistorySeriesVO vo = new HistorySeriesVO();
        vo.setPondId(resolved.pondId);
        vo.setPondName(pondManager.nameOf(resolved.pondId));
        vo.setFrom(resolved.from);
        vo.setTo(resolved.to);
        vo.setCurrentPondTime(resolved.currentPondTime);
        vo.setRetentionDays(retentionDays);
        vo.setBucketMinutes(result.bucketMinutes());
        vo.setSampleCount(result.sampleCount());
        vo.setSummary(toSummary(result.summary()));
        vo.setPoints(result.points().stream()
                .map(point -> new HistorySeriesVO.Point(
                        point.time(), point.doValue(), point.temperature(),
                        point.aeratorOn(), point.ph()))
                .toList());
        return vo;
    }

    @Override
    public CsvExport export(HistoryQueryDTO query) {
        HistorySeriesVO vo = series(query);
        StringBuilder csv = new StringBuilder();
        csv.append((char) 0xFEFF).append("时间,溶氧(mg/L),水温(℃),pH,增氧机状态\n");
        for (HistorySeriesVO.Point point : vo.getPoints()) {
            csv.append(point.time().format(INPUT_TIME)).append(',')
                    .append(String.format(Locale.ROOT, "%.2f", point.doValue())).append(',')
                    .append(String.format(Locale.ROOT, "%.2f", point.temperature())).append(',')
                    .append(point.ph() > 0 ? String.format(Locale.ROOT, "%.2f", point.ph()) : "").append(',')
                    .append(point.aeratorOn() ? "开启" : "关闭").append('\n');
        }
        String filename = "history-pond" + vo.getPondId() + "-"
                + vo.getFrom().format(FILE_TIME) + "-" + vo.getTo().format(FILE_TIME) + ".csv";
        return new CsvExport(filename, csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Resolved resolve(HistoryQueryDTO query) {
        HistoryQueryDTO safe = query == null ? new HistoryQueryDTO() : query;
        Long pondId = safe.getPondId();
        if (pondId == null) {
            List<Long> ids = pondManager.pondIds();
            if (ids.isEmpty()) {
                throw new BusinessException("还没有可查询的鱼塘");
            }
            pondId = ids.get(0);
        }
        SimulationEngine engine = pondManager.requireEngine(pondId);
        LocalDateTime currentPondTime = engine.getSnapshot().pondTime();
        if (currentPondTime == null) {
            throw new BusinessException("鱼塘时间尚未初始化，请稍后再试");
        }

        LocalDateTime to = safe.getTo() == null ? currentPondTime : safe.getTo();
        LocalDateTime from = safe.getFrom() == null ? to.minusDays(1) : safe.getFrom();
        if (to.isAfter(currentPondTime)) {
            to = currentPondTime;
        }
        if (!from.isBefore(to)) {
            throw new BusinessException("开始时间必须早于结束时间");
        }
        long minutes = Duration.between(from, to).toMinutes();
        if (minutes < 1) {
            throw new BusinessException("时间范围至少 1 分钟");
        }
        long maxMinutes = (long) retentionDays * 24 * 60;
        if (minutes > maxMinutes) {
            throw new BusinessException("历史记录最多查看 " + retentionDays + " 天");
        }
        return new Resolved(pondId, from, to, currentPondTime);
    }

    private static HistorySeriesVO.Summary toSummary(HistoryAggregator.Summary summary) {
        return new HistorySeriesVO.Summary(
                summary.avgDo(), summary.minDo(), summary.maxDo(),
                summary.avgTemperature(), summary.minTemperature(), summary.maxTemperature(),
                summary.aeratorMinutes(), summary.lowDoMinutes(),
                summary.onThreshold(), summary.offThreshold(), summary.safetyFloor(),
                summary.avgPh(), summary.minPh(), summary.maxPh());
    }

    private record Resolved(Long pondId, LocalDateTime from, LocalDateTime to,
                            LocalDateTime currentPondTime) {
    }
}

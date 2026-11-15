package com.example.fished.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.fished.common.BusinessException;
import com.example.fished.common.enums.AeratorActionEnum;
import com.example.fished.entity.AeratorLog;
import com.example.fished.entity.PondConfig;
import com.example.fished.mapper.AeratorLogMapper;
import com.example.fished.service.ElectricityCostService;
import com.example.fished.service.ElectricityPriceService;
import com.example.fished.sim.PondManager;
import com.example.fished.sim.PondSnapshot;
import com.example.fished.sim.SimulationEngine;
import com.example.fished.vo.ElectricityCostVO;
import com.example.fished.vo.ElectricityVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 增氧机电费统计：从 aerator_log 还原真实开机区间，按鱼塘时间与分时电价估算。
 * 只做只读统计，不修改任何仿真状态。
 */
@Service
@Slf4j
public class ElectricityCostServiceImpl implements ElectricityCostService {

    private static final int HOURS_PER_DAY = 24;
    private static final BigDecimal DEFAULT_POWER_KW = new BigDecimal("1.50");

    private final PondManager pondManager;
    private final ElectricityPriceService electricityPriceService;
    private final AeratorLogMapper aeratorLogMapper;

    public ElectricityCostServiceImpl(PondManager pondManager,
                                      ElectricityPriceService electricityPriceService,
                                      AeratorLogMapper aeratorLogMapper) {
        this.pondManager = pondManager;
        this.electricityPriceService = electricityPriceService;
        this.aeratorLogMapper = aeratorLogMapper;
    }

    @Override
    public ElectricityCostVO getCost(Long pondId) {
        Long targetId = resolvePondId(pondId);
        PondConfig config = pondManager.requireConfig(targetId);
        SimulationEngine engine = pondManager.requireEngine(targetId);
        PondSnapshot snapshot = engine.getSnapshot();
        LocalDateTime end = snapshot.pondTime();
        LocalDate today = end.toLocalDate();
        LocalDateTime start = today.minusDays(29).atStartOfDay();

        BigDecimal powerKw = config.getAeratorPowerKw() != null
                && config.getAeratorPowerKw().signum() > 0
                ? config.getAeratorPowerKw() : DEFAULT_POWER_KW;

        ElectricityVO priceConfig = electricityPriceService.getConfig();
        List<BigDecimal> hourlyPrices = toHourlyPrices(priceConfig);
        List<String> hourlyTypes = toHourlyTypes(priceConfig);

        AeratorLog lastBefore = aeratorLogMapper.selectOne(new LambdaQueryWrapper<AeratorLog>()
                .eq(AeratorLog::getPondId, targetId)
                .lt(AeratorLog::getPondTime, start)
                .orderByDesc(AeratorLog::getPondTime)
                .orderByDesc(AeratorLog::getId)
                .last("LIMIT 1"));
        boolean runningAtStart = lastBefore != null
                && AeratorActionEnum.ON.getCode().equals(lastBefore.getAction());

        List<AeratorLog> events = aeratorLogMapper.selectList(new LambdaQueryWrapper<AeratorLog>()
                .eq(AeratorLog::getPondId, targetId)
                .ge(AeratorLog::getPondTime, start)
                .le(AeratorLog::getPondTime, end)
                .orderByAsc(AeratorLog::getPondTime)
                .orderByAsc(AeratorLog::getId));

        Map<LocalDate, ElectricityCostCalculator.DayCost> days = ElectricityCostCalculator.calculate(
                start, end, runningAtStart, events, powerKw, hourlyPrices, hourlyTypes);

        ElectricityCostVO vo = new ElectricityCostVO();
        vo.setPondId(targetId);
        vo.setPondName(config.getPondName());
        vo.setPowerKw(powerKw.setScale(2, RoundingMode.HALF_UP));
        vo.setPondTime(end);
        vo.setAeratorOn(snapshot.aeratorOn());

        fillCurrentPeriod(vo, priceConfig, end);

        vo.setToday(summary(days, today, today));
        vo.setLast7Days(summary(days, today.minusDays(6), today));
        vo.setLast30Days(summary(days, today.minusDays(29), today));

        List<ElectricityCostVO.Daily> daily = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            ElectricityCostCalculator.DayCost day = days.get(date);
            daily.add(new ElectricityCostVO.Daily(
                    date.toString(),
                    money(day == null ? BigDecimal.ZERO : day.energy(), 3),
                    money(day == null ? BigDecimal.ZERO : day.cost(), 2),
                    runtimeMinutes(day),
                    money(day == null ? BigDecimal.ZERO : day.peakCost(), 2),
                    money(day == null ? BigDecimal.ZERO : day.flatCost(), 2),
                    money(day == null ? BigDecimal.ZERO : day.valleyCost(), 2)));
        }
        vo.setDaily(daily);
        return vo;
    }

    private Long resolvePondId(Long pondId) {
        if (pondId != null) {
            return pondId;
        }
        List<Long> ids = pondManager.pondIds();
        if (ids.isEmpty()) {
            throw new BusinessException("暂无鱼塘，无法统计电费");
        }
        return ids.get(0);
    }

    private static List<BigDecimal> toHourlyPrices(ElectricityVO priceConfig) {
        List<BigDecimal> prices = new ArrayList<>(Collections.nCopies(HOURS_PER_DAY, (BigDecimal) null));
        if (priceConfig == null || priceConfig.getHourlyPrices() == null) {
            return prices;
        }
        List<BigDecimal> source = priceConfig.getHourlyPrices();
        for (int i = 0; i < Math.min(HOURS_PER_DAY, source.size()); i++) {
            prices.set(i, source.get(i));
        }
        return prices;
    }

    private static List<String> toHourlyTypes(ElectricityVO priceConfig) {
        List<String> types = new ArrayList<>(Collections.nCopies(HOURS_PER_DAY, "flat"));
        if (priceConfig == null || priceConfig.getPeriods() == null) {
            return types;
        }
        for (ElectricityVO.Period period : priceConfig.getPeriods()) {
            int from = Math.max(0, period.startHour());
            int to = Math.min(HOURS_PER_DAY, period.endHour());
            for (int h = from; h < to; h++) {
                types.set(h, period.periodType());
            }
        }
        return types;
    }

    private static void fillCurrentPeriod(ElectricityCostVO vo, ElectricityVO priceConfig, LocalDateTime pondTime) {
        if (priceConfig == null || priceConfig.getPeriods() == null) {
            return;
        }
        int hour = pondTime.getHour();
        priceConfig.getPeriods().stream()
                .filter(p -> hour >= p.startHour() && hour < p.endHour())
                .findFirst()
                .ifPresent(p -> {
                    vo.setCurrentPeriodType(p.periodType());
                    vo.setCurrentPeriodDesc(p.periodDesc());
                    vo.setCurrentPrice(p.price());
                });
    }

    private static ElectricityCostVO.Summary summary(
            Map<LocalDate, ElectricityCostCalculator.DayCost> days,
            LocalDate from, LocalDate to) {
        BigDecimal energy = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal flat = BigDecimal.ZERO;
        BigDecimal valley = BigDecimal.ZERO;
        long millis = 0;
        for (Map.Entry<LocalDate, ElectricityCostCalculator.DayCost> entry : days.entrySet()) {
            LocalDate date = entry.getKey();
            if (date.isBefore(from) || date.isAfter(to)) {
                continue;
            }
            ElectricityCostCalculator.DayCost day = entry.getValue();
            energy = energy.add(day.energy());
            cost = cost.add(day.cost());
            peak = peak.add(day.peakCost());
            flat = flat.add(day.flatCost());
            valley = valley.add(day.valleyCost());
            millis += day.millis();
        }
        return new ElectricityCostVO.Summary(
                money(energy, 3), money(cost, 2), Math.round(millis / 60000.0),
                money(peak, 2), money(flat, 2), money(valley, 2));
    }

    private static long runtimeMinutes(ElectricityCostCalculator.DayCost day) {
        return day == null ? 0 : Math.round(day.millis() / 60000.0);
    }

    private static BigDecimal money(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP);
    }
}

package com.example.fished.sim;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.fished.common.BusinessException;
import com.example.fished.entity.AeratorStrategy;
import com.example.fished.entity.PondConfig;
import com.example.fished.entity.SimSetting;
import com.example.fished.mapper.AeratorStrategyMapper;
import com.example.fished.mapper.PondConfigMapper;
import com.example.fished.mapper.SimSettingMapper;
import com.example.fished.sim.event.SimEvent;
import com.example.fished.vo.PondVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多鱼塘运行中心：
 * - 启动时按 pond_config 的每一行创建一套独立仿真引擎；
 * - 每秒推进全部鱼塘；
 * - 新增鱼塘时同步创建策略、系统设置与仿真引擎。
 */
@Slf4j
@Component
public class PondManager {

    private final PondConfigMapper pondConfigMapper;
    private final AeratorStrategyMapper strategyMapper;
    private final SimSettingMapper settingMapper;

    private final Map<Long, SimulationEngine> engines = new ConcurrentHashMap<>();
    private final Map<Long, String> pondNames = new ConcurrentHashMap<>();

    public PondManager(PondConfigMapper pondConfigMapper,
                       AeratorStrategyMapper strategyMapper,
                       SimSettingMapper settingMapper) {
        this.pondConfigMapper = pondConfigMapper;
        this.strategyMapper = strategyMapper;
        this.settingMapper = settingMapper;
    }

    @PostConstruct
    public synchronized void initialize() {
        List<PondConfig> configs = listConfigs();
        if (configs.isEmpty()) {
            PondConfig config = new PondConfig();
            config.setPondName("一号塘");
            pondConfigMapper.insert(config);
            config = pondConfigMapper.selectById(config.getId());
            configs = List.of(config);
            log.info("pond_config 为空，已自动创建默认鱼塘 #{}", config.getId());
        }

        for (PondConfig config : configs) {
            try {
                register(config);
            } catch (Exception e) {
                log.error("鱼塘 #{} 初始化失败（已跳过，其他鱼塘继续运行）", config.getId(), e);
            }
        }
        log.info("多鱼塘初始化完成：共 {} 个鱼塘 {}", engines.size(), pondIds());
    }

    /** 按 pond_config 主键顺序返回鱼塘 ID。 */
    public List<Long> pondIds() {
        List<Long> ids = new ArrayList<>(engines.keySet());
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    public int count() {
        return engines.size();
    }

    public String nameOf(Long pondId) {
        String name = pondNames.get(pondId);
        return name == null ? "鱼塘#" + pondId : name;
    }

    public SimulationEngine requireEngine(Long pondId) {
        SimulationEngine engine = pondId == null ? null : engines.get(pondId);
        if (engine == null) {
            throw new BusinessException("鱼塘不存在或尚未初始化：#" + pondId);
        }
        return engine;
    }

    public PondConfig requireConfig(Long pondId) {
        PondConfig config = pondId == null ? null : pondConfigMapper.selectById(pondId);
        if (config == null) {
            throw new BusinessException("鱼塘配置不存在：#" + pondId);
        }
        return config;
    }

    /** 推进所有鱼塘，并汇总各鱼塘产生的事件。 */
    public List<SimEvent> tickAll() {
        List<SimEvent> events = new ArrayList<>();
        for (Long pondId : pondIds()) {
            SimulationEngine engine = engines.get(pondId);
            if (engine == null) continue;
            try {
                events.addAll(engine.tick());
            } catch (Exception e) {
                log.error("鱼塘 #{} 仿真 tick 异常（已捕获，定时器继续运行）", pondId, e);
            }
        }
        return events;
    }

    public List<PondVO> listPonds() {
        return listConfigs().stream()
                .map(config -> {
                    SimulationEngine engine = engines.get(config.getId());
                    return PondVO.from(config, engine == null ? null : engine.getSnapshot());
                })
                .toList();
    }

    /**
     * 新增一个鱼塘。水体参数复制自当前第一个鱼塘，策略与系统设置使用数据库默认值。
     * 新鱼塘从今天 00:00、初始溶氧 6.00 mg/L 开始独立运行。
     */
    @Transactional
    public synchronized PondVO createPond(String pondName) {
        String name = pondName == null ? "" : pondName.trim();
        if (name.isEmpty()) {
            throw new BusinessException("鱼塘名称不能为空");
        }
        if (name.length() > 64) {
            throw new BusinessException("鱼塘名称最多 64 个字符");
        }
        Long sameName = pondConfigMapper.selectCount(
                new LambdaQueryWrapper<PondConfig>().eq(PondConfig::getPondName, name));
        if (sameName != null && sameName > 0) {
            throw new BusinessException("鱼塘名称已存在：" + name);
        }

        List<PondConfig> configs = listConfigs();
        PondConfig config = new PondConfig();
        if (!configs.isEmpty()) {
            BeanUtils.copyProperties(configs.get(0), config,
                    "id", "createTime", "updateTime", "isDelete");
        }
        config.setPondName(name);
        pondConfigMapper.insert(config);

        Long pondId = config.getId();
        AeratorStrategy strategy = new AeratorStrategy();
        strategy.setPondId(pondId);
        strategyMapper.insert(strategy);

        SimSetting setting = new SimSetting();
        setting.setPondId(pondId);
        settingMapper.insert(setting);

        register(pondConfigMapper.selectById(pondId));
        log.info("新增鱼塘 #{}（{}），已创建独立策略、系统设置与仿真引擎", pondId, name);
        return PondVO.from(pondConfigMapper.selectById(pondId), requireEngine(pondId).getSnapshot());
    }

    private void register(PondConfig config) {
        Long pondId = config.getId();
        AeratorStrategy strategy = findStrategy(pondId);
        if (strategy == null) {
            strategy = new AeratorStrategy();
            strategy.setPondId(pondId);
            strategyMapper.insert(strategy);
            strategy = findStrategy(pondId);
            log.info("鱼塘 #{} 缺少增氧策略，已创建默认策略", pondId);
        }

        SimSetting setting = findSetting(pondId);
        if (setting == null) {
            setting = new SimSetting();
            setting.setPondId(pondId);
            settingMapper.insert(setting);
            setting = findSetting(pondId);
            log.info("鱼塘 #{} 缺少系统设置，已创建默认设置", pondId);
        }

        engines.put(pondId, buildEngine(config, strategy, setting));
        pondNames.put(pondId, config.getPondName());
    }

    private SimulationEngine buildEngine(PondConfig config, AeratorStrategy strategy, SimSetting setting) {
        SimParams simParams = SimAssembler.toSimParams(config);

        StrategyParams strategyParams;
        try {
            strategyParams = SimAssembler.toStrategyParams(strategy, setting);
        } catch (BusinessException e) {
            log.warn("鱼塘 #{} 的增氧策略不合法（{}），先退回默认策略", config.getId(), e.getMessage());
            strategyParams = StrategyParams.defaults();
        }

        int stepSeconds = setting == null || setting.getStepSeconds() == null
                ? 60 : setting.getStepSeconds();
        int sampleIntervalSec = setting == null || setting.getSampleIntervalSec() == null
                ? 60 : setting.getSampleIntervalSec();
        LocalDateTime startTime = LocalDate.now().atStartOfDay();

        log.info("鱼塘 #{}「{}」引擎初始化：起始={}，步长={}秒，采样={}秒",
                config.getId(), config.getPondName(), startTime, stepSeconds, sampleIntervalSec);
        return new SimulationEngine(config.getId(), simParams, strategyParams,
                stepSeconds, sampleIntervalSec, startTime, SimulationEngine.INITIAL_DO);
    }

    private List<PondConfig> listConfigs() {
        return pondConfigMapper.selectList(
                new LambdaQueryWrapper<PondConfig>().orderByAsc(PondConfig::getId));
    }

    private AeratorStrategy findStrategy(Long pondId) {
        return strategyMapper.selectOne(new LambdaQueryWrapper<AeratorStrategy>()
                .eq(AeratorStrategy::getPondId, pondId)
                .orderByAsc(AeratorStrategy::getId)
                .last("LIMIT 1"));
    }

    private SimSetting findSetting(Long pondId) {
        return settingMapper.selectOne(new LambdaQueryWrapper<SimSetting>()
                .eq(SimSetting::getPondId, pondId)
                .orderByAsc(SimSetting::getId)
                .last("LIMIT 1"));
    }
}

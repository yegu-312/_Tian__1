package com.example.fished.sim;

import com.example.fished.service.DoSampleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PersistenceScheduler {
    private final PondManager pondManager;
    private final DoSampleService doSampleService;

    /** 每个鱼塘各自维护落库水位，互不影响。 */
    private final Map<Long, Long> persistedUpToMinute = new ConcurrentHashMap<>();

    public PersistenceScheduler(PondManager pondManager, DoSampleService doSampleService) {
        this.pondManager = pondManager;
        this.doSampleService = doSampleService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void persist() {
        for (Long pondId : pondManager.pondIds()) {
            try {
                persistPond(pondId, pondManager.requireEngine(pondId));
            } catch (Exception e) {
                log.error("鱼塘 #{} 溶氧降采样落库失败（已捕获，定时器继续运行）", pondId, e);
            }
        }
    }

    private void persistPond(Long pondId, SimulationEngine engine) {
        List<DoPoint> all = engine.getAllHistory();
        if (all.isEmpty()) {
            return;
        }
        long maxOffset = all.get(all.size() - 1).minuteOffset();
        long watermark = persistedUpToMinute.getOrDefault(pondId, Long.MIN_VALUE);
        if (watermark != Long.MIN_VALUE && maxOffset < watermark) {
            log.info("鱼塘 #{} 检测到模拟已重置（落库水位 {} → 当前横轴 {}），水位一并复位",
                    pondId, watermark, maxOffset);
            watermark = Long.MIN_VALUE;
        }

        final long lowerBound = watermark;
        List<DoPoint> fresh = all.stream().filter(p -> p.minuteOffset() > lowerBound).toList();
        if (fresh.isEmpty()) {
            return;
        }
        PondSnapshot anchor = engine.getSnapshot();

        int written = doSampleService.persist(pondId, fresh, anchor);
        persistedUpToMinute.put(pondId, maxOffset);

        log.info("鱼塘 #{} 溶氧降采样落库：写入 {} 个点，横轴 {} → {}，鱼塘时间 {} → {}",
                pondId, written, fresh.get(0).minuteOffset(), maxOffset,
                SimAssembler.absoluteTime(anchor, fresh.get(0)),
                SimAssembler.absoluteTime(anchor, fresh.get(fresh.size() - 1)));
    }
}

package com.example.fished.sim;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.fished.entity.DoSample;
import com.example.fished.mapper.DoSampleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 历史采样保留策略：每天凌晨清理超过保留天数的 do_sample 数据。
 * 以鱼塘仿真时间为准，默认保留 31 天，可通过 app.history.retention-days 调整。
 */
@Slf4j
@Component
public class HistoryRetentionScheduler {

    private static final int BATCH_SIZE = 5000;

    private final PondManager pondManager;
    private final DoSampleMapper doSampleMapper;

    @Value("${app.history.retention-days:31}")
    private int retentionDays;

    public HistoryRetentionScheduler(PondManager pondManager, DoSampleMapper doSampleMapper) {
        this.pondManager = pondManager;
        this.doSampleMapper = doSampleMapper;
    }

    @Scheduled(cron = "0 40 3 * * *")
    public void purgeExpiredSamples() {
        for (Long pondId : pondManager.pondIds()) {
            try {
                purgePond(pondId);
            } catch (Exception e) {
                log.error("鱼塘 #{} 历史记录清理失败（已捕获，其他鱼塘继续）", pondId, e);
            }
        }
    }

    private void purgePond(Long pondId) {
        LocalDateTime current = pondManager.requireEngine(pondId).getSnapshot().pondTime();
        if (current == null) {
            return;
        }
        LocalDateTime cutoff = current.minusDays(retentionDays);
        int total = 0;
        int removed;
        do {
            removed = doSampleMapper.delete(new LambdaQueryWrapper<DoSample>()
                    .eq(DoSample::getPondId, pondId)
                    .lt(DoSample::getPondTime, cutoff)
                    .last("LIMIT " + BATCH_SIZE));
            total += removed;
        } while (removed >= BATCH_SIZE);

        if (total > 0) {
            log.info("鱼塘 #{} 历史记录清理：删除 {} 条，保留最近 {} 天（截止 {}）",
                    pondId, total, retentionDays, cutoff);
        }
    }
}

package com.example.fished.controller;

import com.example.fished.common.BusinessException;
import com.example.fished.common.Result;
import com.example.fished.sim.DoPoint;
import com.example.fished.sim.PondManager;
import com.example.fished.sim.PondSnapshot;
import com.example.fished.sim.SimulationEngine;
import com.example.fished.sim.StrategyParams;
import com.example.fished.vo.PondRealtimeVO;
import com.example.fished.vo.RealtimeVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
//实时监控
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private static final int DEFAULT_PREDICT_MINUTES=30;
    private static final int MAX_PREDICT_MINUTES=240;

    private final PondManager pondManager;

    public MonitorController(PondManager pondManager){
        this.pondManager = pondManager;
    }
    //一次返回全部内容：当前状态 + 历史曲线 +预测曲线 + 三条阈值线
    @GetMapping("/realtime")
    public Result<RealtimeVO> realtime(@RequestParam(defaultValue = "1") Long pondId,
                                         @RequestParam(defaultValue = "30") int predictMinutes,
                                         @RequestParam(defaultValue = "60") int historyLimit){//historyLimit：默认 60 条，最大 2000 条
        SimulationEngine engine = pondManager.requireEngine(pondId);
        return Result.success(buildRealtime(engine, predictMinutes, historyLimit));
    }
    //获取历史数据
    @GetMapping("/history")
    public Result<List<DoPoint>> history(@RequestParam(defaultValue = "1") Long pondId,
                                         @RequestParam(defaultValue="60")int limit){
        int safe = Math.max(1, Math.min(limit, 2000));
        return Result.success(pondManager.requireEngine(pondId).getHistory(safe));
    }
    //查看仿真引擎里缓存了多少条历史溶氧数据
    @GetMapping("/buffer")
    public Result<Integer> buffer(@RequestParam(defaultValue = "1") Long pondId){
        return Result.success(pondManager.requireEngine(pondId).getHistorySize());
    }
    /**
     * 双塘/多塘对比：一次返回多个鱼塘的完整实时数据。
     * pondIds 省略时返回全部鱼塘，例如 pondIds=1,2。
     */
    @GetMapping("/compare")
    public Result<List<PondRealtimeVO>> compare(
            @RequestParam(required = false) String pondIds,
            @RequestParam(defaultValue = "30") int predictMinutes,
            @RequestParam(defaultValue = "60") int historyLimit) {
        List<Long> ids = parsePondIds(pondIds);
        List<PondRealtimeVO> result = new ArrayList<>();
        for (Long pondId : ids) {
            SimulationEngine engine = pondManager.requireEngine(pondId);
            result.add(new PondRealtimeVO(
                    pondId,
                    pondManager.nameOf(pondId),
                    buildRealtime(engine, predictMinutes, historyLimit)));
        }
        return Result.success(result);
    }

    private RealtimeVO buildRealtime(SimulationEngine engine, int predictMinutes, int historyLimit) {
        int minutes=Math.max(0,Math.min(predictMinutes,MAX_PREDICT_MINUTES));
        int limit=Math.max(1,Math.min(historyLimit,2000));
        StrategyParams params = engine.getStrategyParams();

        RealtimeVO vo = new RealtimeVO();
        vo.setCurrentDo(engine.getSnapshot());//当前状态
        vo.setHistory(engine.getHistory(limit)); //最近 limit个历史数据（从旧到新）
        vo.setPrediction(engine.predict(minutes));//未来 minutes 分钟的预测曲线
        vo.setThresholds(new RealtimeVO.Thresholds(params.safetyFloor(),
                params.onThreshold(),params.offThreshold()));//三条阈值参考线
        return vo;
    }

    private List<Long> parsePondIds(String pondIds) {
        if (!StringUtils.hasText(pondIds)) {
            return pondManager.pondIds();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : pondIds.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) continue;
            try {
                ids.add(Long.parseLong(value));
            } catch (NumberFormatException e) {
                throw new BusinessException("鱼塘 ID 格式不正确：" + value);
            }
        }
        if (ids.isEmpty()) {
            throw new BusinessException("请至少指定一个鱼塘");
        }
        return ids;
    }
}

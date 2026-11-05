package com.example.fished.sim;

import com.example.fished.common.BusinessException;
import com.example.fished.common.enums.AlertTypeEnum;
import com.example.fished.sim.event.AlertRaisedEvent;
import com.example.fished.sim.event.AlertRecoveredEvent;
import com.example.fished.sim.event.SimEvent;
import lombok.extern.slf4j.Slf4j;
import com.example.fished.sim.event.AeratorStateChangedEvent;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class SimulationEngine {
    private static final int BUFFER_CAPACITY = 2000;
    public static final double INITIAL_DO=6.0;
    public static final int MAX_SPEED=3600;
    private static final double NOISE_SCALE=0.02;

    private final long pondId;
    private final ReentrantLock lock=new ReentrantLock();
    private final PondState state;
    private final DoRingBuffer history=new DoRingBuffer(BUFFER_CAPACITY);
    private volatile PondSnapshot snapshot;
    private final AeratorDecider decider=new AeratorDecider();
    private final Random random=new Random();

    private volatile SimParams simParams;
    private volatile StrategyParams strategyParams;
    private volatile int stepSeconds;
    private volatile int sampleIntervalSec;

    public SimulationEngine(long pondId, SimParams simParams, StrategyParams strategyParams,
                            int stepSeconds, int sampleIntervalSec,
                            LocalDateTime startTime,double initialDo) {
        this.pondId = pondId;
        this.simParams=simParams;
        this.strategyParams=strategyParams;
        this.stepSeconds=stepSeconds;
        this.sampleIntervalSec=sampleIntervalSec;
        this.state=new PondState(
                startTime,
                initialDo,
                Physics.waterTemperature(startTime,simParams));
        this.snapshot=buildSnapshot();
    }
    public List<SimEvent> tick() {
        lock.lock();
        try {
            if (state.isPaused()) {
                return List.of();
            }
            List<SimEvent> events = new ArrayList<>(3);
            events.addAll(advance());
            events.addAll(checkAlerts());
            sample();
            snapshot = buildSnapshot();
            return events;
        } finally {
            lock.unlock();
        }
    }
    private  List<SimEvent> advance() {
        long advanceSeconds = (long) stepSeconds * state.getSpeed();
        long advanceMinutes = advanceSeconds / 60;
        state.advance(state.getPondTime().plusSeconds(advanceSeconds), advanceMinutes);

        double temperature = Physics.waterTemperature(state.getPondTime(), simParams);
        double light = Physics.daylightFactor(state.getPondTime());

        double dtHours = advanceSeconds / 3600.0;
        double noise = random.nextGaussian() * NOISE_SCALE;
        double nextDo = Physics.nextDo(
                state.getDoValue(), temperature,
                light,
                state.isAeratorOn(), simParams,
                dtHours, noise);

        state.setTemperature(temperature);
        state.setDoValue(nextDo);

        Double predictedMin=null;
        int predictMinutes= strategyParams.predictMinutes();
        if(predictMinutes>0){
            PondSnapshot current = buildSnapshot();
            predictedMin=DoPredictor.predictMin(current.currentDo(),current.pondTime(),
                    current.aeratorOn(),simParams,predictMinutes);
        }
        List<SimEvent> events = new ArrayList<>(1);
        Decision decision = decider.decide(buildSnapshot(), strategyParams, predictedMin);
        if (decision.shouldLog()) {
            state.setAeratorOn(decision.on());
            events.add(AeratorStateChangedEvent.auto(
                    pondId,
                    state.getPondTime(),
                    decision.on(),
                    decision.reason(),
                    state.getDoValue()));
        }
        return events;
    }

    private List<SimEvent> checkAlerts() {
    List<SimEvent> events = new ArrayList<>(2);
    LocalDateTime now = state.getPondTime();
    double doValue = state.getDoValue();

    boolean safetyLow = doValue <strategyParams.safetyFloor();
    if (safetyLow && !state.isSafetyAlertActive())
    {
        state.setSafetyAlertActive(true);
        events.add(AlertRaisedEvent.safetyFloor(pondId, now, doValue, strategyParams.safetyFloor()));
    } else if (!safetyLow && state.isSafetyAlertActive()) {

        state.setSafetyAlertActive(false);
        events.add(new AlertRecoveredEvent(pondId, now, AlertTypeEnum.DO_SAFETY_FLOOR, doValue));
    }

    boolean thresholdLow = doValue < strategyParams.onThreshold();
    if (thresholdLow && !state.isThresholdAlertActive()) {
        state.setThresholdAlertActive(true);

        events.add(AlertRaisedEvent.onThreshold(pondId, now, doValue, strategyParams.onThreshold()));
    } else if (!thresholdLow &&
            state.isThresholdAlertActive()) {
        state.setThresholdAlertActive(false);
        events.add(new AlertRecoveredEvent(pondId, now,
                AlertTypeEnum.DO_ON_THRESHOLD, doValue));
    }
    return events;
}
    private void sample(){
        int intervalMinutes=Math.max(1,sampleIntervalSec/60);
        history.sampleIfDue(
                new DoPoint(
                        state.getElapsedMinutes(),round2(state.getDoValue()),
                        round2(state.getTemperature()),state.isAeratorOn()),
                        intervalMinutes);
    }

    private PondSnapshot buildSnapshot(){
        return new PondSnapshot(
                state.getPondTime(),round2(state.getDoValue()),
                round2(state.getTemperature()),state.isAeratorOn(),
                state.isPaused(),state.getSpeed(),state.getElapsedMinutes());
    }
    public long getPondId() {
        return pondId;
    }

    //获得当前对象最新状态
    public PondSnapshot getSnapshot(){
        return snapshot;
    }
    //历史数据
    public List<DoPoint> getHistory(int limit){
        lock.lock();
        try{
            return history.latest(limit);
        }finally {
            lock.unlock();
        }
    }
    public int getHistorySize() {
        lock.lock();
        try {
            return history.size();
        } finally {
            lock.unlock();
        }
    }

    public List<DoPoint> getAllHistory(){
        lock.lock();
        try{
            return history.latest(history.size());
        }finally {
            lock.unlock();
        }
    }
    //未来预测
    public List<DoPoint> predict(int minutesAhead) {
        PondSnapshot current = snapshot;
        return DoPredictor.predict(
                current.currentDo(), current.pondTime(), current.elapsedMinutes(),
                current.aeratorOn(), simParams, minutesAhead);
    }
    //返回增氧策略的配置参数
    public StrategyParams getStrategyParams() {
        return strategyParams;
    }

    //安全地把仿真引擎暂停，并返回暂停那一刻的快照
    public PondSnapshot pause(){
        lock.lock();
        try {
            state.setPaused(true);//状态设为暂停
            snapshot = buildSnapshot();
            return snapshot;
        } finally {
            lock.unlock();
        }
    }

    public PondSnapshot resume() {
        lock.lock();
        try {
            state.setPaused(false);
            snapshot = buildSnapshot();// 重新生成一份快照发布出去
            return snapshot;
        } finally {
            lock.unlock();
        }
    }

    //改倍速
    public PondSnapshot setSpeed(int speed) {
        if (speed < 1 || speed > MAX_SPEED) {
            throw new BusinessException("倍速必须在 1~ " + MAX_SPEED + " 之间");
        }
        lock.lock();
        try {
            state.setSpeed(speed);
            snapshot = buildSnapshot();
            return snapshot;
        } finally {
            lock.unlock();
        }
    }
    //调整时间变为指定
    public PondSnapshot setPondTime(LocalDateTime pondTime) {
        lock.lock();
        try {
            state.setPondTime(pondTime);
            state.setTemperature(Physics.waterTemperature(pondTime, simParams));
            // 故意不动 elapsedMinutes
            snapshot = buildSnapshot();
            return snapshot;
        } finally {
            lock.unlock();
        }
    }
    //重置时间和参数
    public PondSnapshot reset() {
        lock.lock();
        try {
            LocalDateTime startTime = LocalDate.now().atStartOfDay();
            state.reset(startTime, INITIAL_DO, Physics.waterTemperature(startTime, simParams));
            history.clear();
            snapshot = buildSnapshot();
            return snapshot;
        } finally {
            lock.unlock();
        }
    }

    public void updateConfig(SimParams simParams, StrategyParams strategyParams,
                             int stepSeconds, int sampleIntervalSec) {
        lock.lock();
        try {
            this.simParams = simParams;
            this.strategyParams = strategyParams;
            this.stepSeconds = stepSeconds;
            this.sampleIntervalSec =
                    sampleIntervalSec;
            state.setTemperature(Physics.waterTemperature(state.getPondTime(), simParams));
            snapshot = buildSnapshot();
        } finally {
            lock.unlock();
        }
    }

    private static double round2(double value) {
        return Math.round(value*100.0)/100.0;
    }

}

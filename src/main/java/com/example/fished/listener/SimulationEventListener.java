package com.example.fished.listener;

import com.example.fished.service.AeratorLogService;
import com.example.fished.service.AlertService;
import com.example.fished.sim.event.AeratorStateChangedEvent;
import com.example.fished.sim.event.AlertRaisedEvent;
import com.example.fished.sim.event.AlertRecoveredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
//监听记录情况
@Component
@Slf4j
public class SimulationEventListener {
    private final AlertService alertService;
    private final AeratorLogService aeratorLogService;

    public SimulationEventListener(AeratorLogService aeratorLogService,AlertService alertService) {
        this.aeratorLogService = aeratorLogService;
        this.alertService = alertService;
    }
    //监听增氧机开关状态
    @EventListener
    public void onAeratorStateChanged(AeratorStateChangedEvent event){
        try{
            aeratorLogService.record(event);//记录增氧机开关变化
            log.info("[鱼塘#{}][增氧机] {} —— {}", event.pondId(), event.on() ? "开" : "关", event.reason());
        }catch (Exception e){
            log.error("写增氧机开关记录失败：{}",event,e);
        }
    }
    //监听且输出警告
    @EventListener
    public void onAlertRaised(AlertRaisedEvent event)
    {
        try {
            alertService.raise(event);
            log.warn("[鱼塘#{}][告警-{}] {}：{}",
                    event.pondId(),
                    event.level().getDesc(),
                    event.alertType().getDesc(),
                    event.message());
        } catch (Exception e) {
            log.error("写告警记录失败：{}", event, e);
        }
    }
    //打印警告恢复了
    @EventListener
    public void onAlertRecovered(AlertRecoveredEvent event) {
        log.info("[鱼塘#{}][告警恢复] {} 溶氧回到 {}",
                event.pondId(),
                event.alertType().getDesc(),
                event.doValue());
    }
}

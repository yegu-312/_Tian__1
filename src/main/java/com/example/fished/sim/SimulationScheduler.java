package com.example.fished.sim;

import com.example.fished.sim.event.SimEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Slf4j
@Component
public class SimulationScheduler {
    private final PondManager pondManager;
    private final ApplicationEventPublisher publisher;

    public SimulationScheduler(PondManager pondManager, ApplicationEventPublisher publisher) {
        this.pondManager = pondManager;
        this.publisher = publisher;
    }

    @Scheduled(fixedRate = 1000)
    public void tick(){
        try{
            List<SimEvent> events = pondManager.tickAll();
            for(SimEvent event : events){
                publisher.publishEvent(event);
            }
        }catch(Exception e){
            log.error("模拟tick异常（以捕获，定时器继续运行）",e);
        }
    }
}

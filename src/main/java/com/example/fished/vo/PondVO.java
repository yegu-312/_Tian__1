package com.example.fished.vo;

import com.example.fished.entity.PondConfig;
import com.example.fished.sim.PondSnapshot;
import lombok.Data;

import java.time.LocalDateTime;

/** 鱼塘列表项，同时带一份当前运行状态，供顶部切换器和鱼塘管理页使用。 */
@Data
public class PondVO {

    private Long id;
    private String pondName;
    private LocalDateTime pondTime;
    private double currentDo;
    private double temperature;
    private boolean aeratorOn;
    private boolean paused;
    private int speed;

    public static PondVO from(PondConfig config, PondSnapshot snapshot) {
        PondVO vo = new PondVO();
        vo.setId(config.getId());
        vo.setPondName(config.getPondName());
        if (snapshot != null) {
            vo.setPondTime(snapshot.pondTime());
            vo.setCurrentDo(snapshot.currentDo());
            vo.setTemperature(snapshot.temperature());
            vo.setAeratorOn(snapshot.aeratorOn());
            vo.setPaused(snapshot.paused());
            vo.setSpeed(snapshot.speed());
        }
        return vo;
    }
}

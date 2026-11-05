package com.example.fished.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.fished.dto.SettingUpdateDTO;
import com.example.fished.entity.SimSetting;
import com.example.fished.sim.PondSnapshot;
import com.example.fished.vo.SettingVO;

public interface SimSettingService extends IService<SimSetting> {
    SettingVO getSetting(Long pondId);
    SettingVO updateSetting(Long pondId, SettingUpdateDTO dto, String operator);
    //重置模拟
    PondSnapshot reset(Long pondId, String operator);
}

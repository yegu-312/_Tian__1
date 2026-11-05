package com.example.fished.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.fished.dto.PondConfigUpdateDTO;
import com.example.fished.entity.PondConfig;
import com.example.fished.vo.PondConfigVO;

public interface PondConfigService extends IService<PondConfig> {
    //读配置
    PondConfigVO getConfig(Long pondId);
    // // 改配置 + 热更新 + 写日志
    PondConfigVO updateConfig(Long pondId, PondConfigUpdateDTO dto, String operator);
}

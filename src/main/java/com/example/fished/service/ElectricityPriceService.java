package com.example.fished.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.fished.dto.ElectricityUpdateDTO;
import com.example.fished.entity.ElectricityPrice;
import com.example.fished.vo.ElectricityVO;

public interface ElectricityPriceService extends IService<ElectricityPrice> {
    ElectricityVO getConfig();
    ElectricityVO updateConfig(ElectricityUpdateDTO dto, String operator);
}

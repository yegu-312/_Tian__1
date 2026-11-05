package com.example.fished.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.fished.dto.StrategyUpdateDTO;
import com.example.fished.entity.AeratorStrategy;
import com.example.fished.vo.StrategyVO;

public interface AeratorStrategyService extends IService<AeratorStrategy> {
    //获取当前策略
    StrategyVO getStrategy(Long pondId);
    //更改当前策略
    StrategyVO updateStrategy(Long pondId, StrategyUpdateDTO dto, String operator);

    StrategyVO switchManual(Long pondId, boolean on,String operator);
}

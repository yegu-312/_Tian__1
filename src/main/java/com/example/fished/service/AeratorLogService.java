package com.example.fished.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.fished.common.PageResult;
import com.example.fished.dto.AeratorLogQueryDTO;
import com.example.fished.entity.AeratorLog;
import com.example.fished.sim.event.AeratorStateChangedEvent;
import com.example.fished.vo.AeratorLogVO;

public interface AeratorLogService extends IService<AeratorLog> {
    //操作日志 :开/关、原因、当时的溶氧、鱼塘时间、操作人。
    void record(AeratorStateChangedEvent event);

    PageResult<AeratorLogVO> pageQuery(AeratorLogQueryDTO dto);
}

package com.example.fished.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.fished.common.PageResult;
import com.example.fished.dto.AlertQueryDTO;
import com.example.fished.entity.Alert;
import com.example.fished.sim.event.AlertRaisedEvent;
import com.example.fished.vo.AlertVO;

public interface AlertService extends IService<Alert> {
    //产生一条警告并且写进库里面
    void raise(AlertRaisedEvent event);

    PageResult<AlertVO> pageQuery(AlertQueryDTO dto);
    //标记警告为已确认
    void ack(Long id,String operator);
}

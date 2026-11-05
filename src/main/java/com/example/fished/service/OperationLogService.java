package com.example.fished.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.fished.common.PageResult;
import com.example.fished.common.enums.OperationActionEnum;
import com.example.fished.common.enums.OperationModuleEnum;
import com.example.fished.dto.OperationLogQueryDTO;
import com.example.fished.entity.OperationLog;
import com.example.fished.vo.OperationLogVO;

public interface OperationLogService extends IService<OperationLog> {
    //日志记录，记下"哪个模块、做了什么操作、具体改了啥、谁干的"
    void record(OperationModuleEnum module, OperationActionEnum action,
                String detail, String operator);
    PageResult<OperationLogVO> pageQuery(OperationLogQueryDTO dto);
}

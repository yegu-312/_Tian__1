package com.example.fished.controller;

import com.example.fished.common.PageResult;
import com.example.fished.common.Result;
import com.example.fished.dto.AeratorLogQueryDTO;
import com.example.fished.dto.OperationLogQueryDTO;
import com.example.fished.service.AeratorLogService;
import com.example.fished.service.OperationLogService;
import com.example.fished.vo.AeratorLogVO;
import com.example.fished.vo.OperationLogVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
//系统日志查看
@RestController
@RequestMapping("/api/log")
public class LogController {

    private final OperationLogService operationLogService;

    private final AeratorLogService aeratorLogService;

    public LogController(OperationLogService operationLogService, AeratorLogService aeratorLogService) {
        this.aeratorLogService = aeratorLogService;
        this.operationLogService = operationLogService;
    }
    //人所做之事的操作日志
    @GetMapping("/operation")
    public Result<PageResult<OperationLogVO>> operation(@Valid OperationLogQueryDTO dto) {
        return Result.success(operationLogService.pageQuery(dto));//分页查询人改过什么
    }
    //增氧机日志（机器做了什么）
    @GetMapping("/aerator")
    public Result<PageResult<AeratorLogVO>> aerator(@Valid AeratorLogQueryDTO dto) {
        return Result.success(aeratorLogService.pageQuery(dto));
    }
}

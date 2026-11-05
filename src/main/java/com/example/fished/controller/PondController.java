package com.example.fished.controller;

import com.example.fished.common.Result;
import com.example.fished.common.enums.OperationActionEnum;
import com.example.fished.common.enums.OperationModuleEnum;
import com.example.fished.dto.PondCreateDTO;
import com.example.fished.service.OperationLogService;
import com.example.fished.sim.PondManager;
import com.example.fished.vo.PondVO;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 鱼塘管理：查看全部鱼塘、新增鱼塘。 */
@RestController
@RequestMapping("/api/ponds")
public class PondController {

    private static final String DEFAULT_OPERATOR = "admin";

    private final PondManager pondManager;
    private final OperationLogService operationLogService;

    public PondController(PondManager pondManager, OperationLogService operationLogService) {
        this.pondManager = pondManager;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public Result<List<PondVO>> list() {
        return Result.success(pondManager.listPonds());
    }

    @PostMapping
    public Result<PondVO> create(@Valid @RequestBody PondCreateDTO dto,
                                 @RequestHeader(value = "X-Operator", required = false) String operator) {
        PondVO created = pondManager.createPond(dto.getPondName());
        operationLogService.record(OperationModuleEnum.POND_CONFIG, OperationActionEnum.CREATE,
                "新增鱼塘 #" + created.getId() + "（" + created.getPondName() + "）",
                operatorOf(operator));
        return Result.success(created);
    }

    private static String operatorOf(String header) {
        return StringUtils.hasText(header) ? header : DEFAULT_OPERATOR;
    }
}

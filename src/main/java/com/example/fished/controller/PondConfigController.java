package com.example.fished.controller;

import com.example.fished.common.Result;
import com.example.fished.dto.PondConfigUpdateDTO;
import com.example.fished.service.PondConfigService;
import com.example.fished.vo.PondConfigVO;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
//水体参数
@RestController
@RequestMapping("/api/pond")
public class PondConfigController {
    private static final String DEFAULT_OPERATOR = "admin";
    private final PondConfigService pondConfigService;

    public PondConfigController(PondConfigService pondConfigService){
        this.pondConfigService = pondConfigService;
    }
    //查看参数，将水体参数返回给前端
    @GetMapping("/config")
    public Result<PondConfigVO> get(@RequestParam(defaultValue = "1") Long pondId){
        return Result.success(pondConfigService.getConfig(pondId));
    }
    //修改参数并记录操作人
    @PutMapping("/config")
    public Result<PondConfigVO> update(@RequestParam(defaultValue = "1") Long pondId,
                                       @Valid @RequestBody PondConfigUpdateDTO dto,
                                       @RequestHeader(value = "X-Operator", required = false)String operator){
        return Result.success(pondConfigService.updateConfig(pondId, dto,
                StringUtils.hasText(operator)?operator:DEFAULT_OPERATOR));
    }
}

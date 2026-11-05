package com.example.fished.controller;

import com.example.fished.common.Result;
import com.example.fished.dto.SettingUpdateDTO;
import com.example.fished.service.SimSettingService;
import com.example.fished.sim.PondSnapshot;
import com.example.fished.vo.SettingVO;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
//系统设置
@RestController
@RequestMapping("/api/setting")
public class SettingController {
    private static final String DEFAULT_OPERATOR = "admin";
    private final SimSettingService simSettingService;

    public SettingController(SimSettingService simSettingService) {
        this.simSettingService = simSettingService;
    }
    //看设置
    @GetMapping
    public Result<SettingVO> get(@RequestParam(defaultValue = "1") Long pondId){
        return Result.success(simSettingService.getSetting(pondId));
    }
    //改设置
    @PutMapping
    public Result<SettingVO> update(@RequestParam(defaultValue = "1") Long pondId,
                                    @Valid @RequestBody SettingUpdateDTO dto,
                                    @RequestHeader(value = "X-Operator", required = false) String operator) {
        return
                Result.success(simSettingService.updateSetting(pondId, dto, operatorOf(operator)));
    }
    //重置
    @PostMapping("/reset")
    public Result<PondSnapshot> reset(@RequestParam(defaultValue = "1") Long pondId,
            @RequestHeader(value = "X-Operator", required = false)
            String operator) {
        return
                Result.success(simSettingService.reset(pondId, operatorOf(operator)));
    }
    private static String operatorOf(String header){
        return StringUtils.hasText(header)? header : DEFAULT_OPERATOR;
    }
}

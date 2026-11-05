package com.example.fished.controller;

import com.example.fished.common.Result;
import com.example.fished.dto.ElectricityUpdateDTO;
import com.example.fished.service.ElectricityPriceService;
import com.example.fished.vo.ElectricityVO;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
//电价配置
@RestController
@RequestMapping("/api/electricity")
public class ElectricityController {
    private static final String DEFAULT_OPERATOR="admin";
    private final ElectricityPriceService electricityPriceService;
    public ElectricityController(ElectricityPriceService electricityPriceService) {
        this.electricityPriceService = electricityPriceService;
    }
   //查看电价配置
    @GetMapping
    public Result<ElectricityVO> get() {
        return Result.success(electricityPriceService.getConfig());
    }

    //更新电价配置
    @PutMapping
    public Result<ElectricityVO> update(@Valid @RequestBody ElectricityUpdateDTO dto,
                                        @RequestHeader(value = "X-Operator", required = false)
                                        String operator) {
        return Result.success(electricityPriceService.updateConfig(dto, operatorOf(operator)));
    }

    private static String operatorOf(String header) {
        return StringUtils.hasText(header) ? header :
                DEFAULT_OPERATOR;
    }
}

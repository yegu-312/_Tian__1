package com.example.fished.controller;

import com.example.fished.common.Result;
import com.example.fished.dto.StrategyUpdateDTO;
import com.example.fished.service.AeratorStrategyService;
import com.example.fished.vo.StrategyVO;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/aerator")
public class AeratorController {//增氧机手动控制台,控制什么时候开与关的规则

    private static final String DEFAULT_OPERATOR="admin";//默认操作人

    private final AeratorStrategyService aeratorStrategyService;

    public AeratorController(AeratorStrategyService aeratorStrategyService) {
        this.aeratorStrategyService = aeratorStrategyService;
    }
   //查看当前增氧策略
    @GetMapping("/strategy")
    public Result<StrategyVO> getStrategy(@RequestParam(defaultValue = "1") Long pondId){
        return Result.success(aeratorStrategyService.getStrategy(pondId));
    }

    @PutMapping("/strategy")
    public Result<StrategyVO> updateStrategy(@RequestParam(defaultValue = "1") Long pondId,
                                             @Valid @RequestBody StrategyUpdateDTO dto,
                                             @RequestHeader(value = "X-Operator",required = false) String operator){
        return Result.success(aeratorStrategyService.updateStrategy(pondId, dto, operatorOf(operator)));
    }
    //手动开关增氧机
    @PostMapping("/manual")
    public Result<StrategyVO> manual(@RequestParam(defaultValue = "1") Long pondId,
                                     @RequestParam boolean on,
                                     @RequestHeader (value = "X-Operator",required = false) String operator){
        return Result.success(aeratorStrategyService.switchManual(pondId, on, operatorOf(operator)));//强制开关增氧机,并将模式改为手动
    }
    //有内容返回内容没有返回默认值
    private static String operatorOf(String header){
        return StringUtils.hasText(header)?header:DEFAULT_OPERATOR;
    }
}

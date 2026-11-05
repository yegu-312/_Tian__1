package com.example.fished.controller;

import com.example.fished.common.BusinessException;
import com.example.fished.common.Result;
import com.example.fished.common.enums.OperationActionEnum;
import com.example.fished.common.enums.OperationModuleEnum;
import com.example.fished.service.OperationLogService;
import com.example.fished.sim.PondManager;
import com.example.fished.sim.PondSnapshot;
import com.example.fished.sim.SimulationEngine;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
//鱼塘时钟
@RequestMapping("/api/clock")
@RestController
public class ClockController {
    private static final String DEFAULT_OPERATOR = "admin";
    private final PondManager pondManager;
    private final OperationLogService operationLogService;
    public ClockController(PondManager pondManager, OperationLogService operationLogService) {
        this.pondManager = pondManager;
        this.operationLogService = operationLogService;
    }
    private static String operatorOf(String header){
        return StringUtils.hasText(header) ? header : DEFAULT_OPERATOR;
    }
    //鱼塘时间暂停
    @PostMapping("/pause")
    public Result<PondSnapshot> pause(@RequestParam(defaultValue = "1") Long pondId,
            @RequestHeader(value="X-Operator",required=false)String operator){//取操作人
        SimulationEngine engine = pondManager.requireEngine(pondId);
        PondSnapshot snapshot = engine.pause();//暂停时间
        operationLogService.record(OperationModuleEnum.CLOCK, OperationActionEnum.PAUSE,
                "鱼塘#" + pondId + " 暂停仿真，鱼塘时间停在 " + snapshot.pondTime(), operatorOf(operator));
        return Result.success(snapshot);
    }
    //鱼塘时间继续走
    @PostMapping("/resume")
    public Result<PondSnapshot> resume(@RequestParam(defaultValue = "1") Long pondId,
            @RequestHeader(value="X-Operator",required=false)String operator){
        SimulationEngine engine = pondManager.requireEngine(pondId);
        PondSnapshot snapshot = engine.resume();//推进时间
        operationLogService.record(OperationModuleEnum.CLOCK, OperationActionEnum.RESUME,
                "鱼塘#" + pondId + " 继续仿真，鱼塘时间从 " + snapshot.pondTime() + " 接着走", operatorOf(operator));
        return Result.success(snapshot);

    }
    //更改模拟倍速
    @PostMapping("/speed")
    public Result<PondSnapshot> speed(@RequestParam(defaultValue = "1") Long pondId,
                                      @RequestParam int speed,
                                      @RequestHeader(value="X-Operator",required=false)String operator){

        PondSnapshot snapshot = pondManager.requireEngine(pondId).setSpeed(speed);
        operationLogService.record(OperationModuleEnum.CLOCK, OperationActionEnum.SPEED,
                "鱼塘#" + pondId + " 倍速改为 " + speed + "（1 真实秒 = " + speed + " 模拟秒）", operatorOf(operator));
        return Result.success(snapshot);

    }
    //更改鱼塘时间到某一指定时刻
    @PostMapping("/time")
    public Result<PondSnapshot> setTime(@RequestParam(defaultValue = "1") Long pondId,
                                        @RequestParam String time,
                                        @RequestHeader(value = "X-Operator", required = false) String operator) {
        LocalDateTime pondTime;
        try {
            pondTime=LocalDateTime.parse(time);
        }catch (DateTimeParseException e){
            throw new BusinessException("时间格式错误，应该像这样：2026-09-22T14:00");
        }
        SimulationEngine engine = pondManager.requireEngine(pondId);
        PondSnapshot before = engine.getSnapshot();//修改前时间
        PondSnapshot snapshot = engine.setPondTime(pondTime);//当前时间
        operationLogService.record(OperationModuleEnum.CLOCK, OperationActionEnum.SET_TIME,
                "鱼塘#" + pondId + " 鱼塘时间 " + before.pondTime() + " → " + snapshot.pondTime(), operatorOf(operator));
        return Result.success(snapshot);

    }
}

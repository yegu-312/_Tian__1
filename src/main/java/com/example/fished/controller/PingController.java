package com.example.fished.controller;

import com.example.fished.common.Result;
import com.example.fished.mapper.PondConfigMapper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//体检工具，测试是否有问题
@RestController
@RequestMapping("/api")
@Validated
public class PingController {

    private final PondConfigMapper pondConfigMapper;

    public PingController(PondConfigMapper pondConfigMapper) {
        this.pondConfigMapper = pondConfigMapper;
    }
    //检查 Web 层是否正常
    @GetMapping("/ping")
    public Result<String> ping(){
        return Result.success("pong");
    }
    //测数据库，只看不改
    @GetMapping("/db-check")
    public Result<String> dbCheck(){
        long count = pondConfigMapper.selectCount(null);
        return Result.success("数据库连通，pond_config 共 " +
                count + " 行");
    }
}



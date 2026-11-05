package com.example.fished.controller;

import com.example.fished.common.PageResult;
import com.example.fished.common.Result;
import com.example.fished.dto.AlertQueryDTO;
import com.example.fished.service.AlertService;
import com.example.fished.vo.AlertVO;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alert")
public class AlertController {//警告中心
    private static final String DEFAULT_OPERATOR="admin";//默认操作人,无操作人返回默认这个
    private final AlertService alertService;
    public AlertController(AlertService alertService){
        this.alertService=alertService;
    }
    //列出警告,并分等级/类型/状态
    @GetMapping("/list")
    public Result<PageResult<AlertVO>> list(@Valid AlertQueryDTO dto){
        return Result.success(alertService.pageQuery(dto));
    }
    //用于确认警告
    @PutMapping("/ack/{id}")
    public Result<Void> ack(@PathVariable Long id, @RequestHeader(value="X-Operator",required=false)String operator){
        alertService.ack(id, StringUtils.hasText(operator)?operator:DEFAULT_OPERATOR);
        return Result.success();
    }
}

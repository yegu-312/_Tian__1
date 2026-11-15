package com.example.fished.controller;

import com.example.fished.common.Result;
import com.example.fished.dto.HistoryQueryDTO;
import com.example.fished.service.HistoryService;
import com.example.fished.vo.HistorySeriesVO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鱼塘历史记录（溶氧 / 水温 / 增氧机），按鱼塘仿真时间查询。
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/series")
    public Result<HistorySeriesVO> series(HistoryQueryDTO query) {
        return Result.success(historyService.series(query));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(HistoryQueryDTO query) {
        HistoryService.CsvExport file = historyService.export(query);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(file.content());
    }
}

package com.example.fished.service;

import com.example.fished.dto.HistoryQueryDTO;
import com.example.fished.vo.HistorySeriesVO;

/**
 * 历史记录查询：把 do_sample 原始采样按时间桶聚合后返回给前端。
 */
public interface HistoryService {

    HistorySeriesVO series(HistoryQueryDTO query);

    /** 导出当前查询的聚合结果（CSV，UTF-8 BOM，方便 Excel 打开）。 */
    CsvExport export(HistoryQueryDTO query);

    record CsvExport(String filename, byte[] content) {
    }
}

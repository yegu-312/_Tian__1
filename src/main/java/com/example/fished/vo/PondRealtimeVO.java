package com.example.fished.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 双塘对比接口的单项：鱼塘信息 + 完整实时数据。 */
@Data
@AllArgsConstructor
public class PondRealtimeVO {

    private Long pondId;
    private String pondName;
    private RealtimeVO realtime;
}

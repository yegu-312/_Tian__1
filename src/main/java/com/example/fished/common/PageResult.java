package com.example.fished.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult <T>{
    private long total;//符合条件的总记录数（不是本页条数）
    private long pages;//总页数
    private long current;//当前页数
    private long size;//每页多少条
    private List<T> records;//本页数据

    public static<T> PageResult<T> of(IPage<?> page,List<T> records){
        return new PageResult<>(
                page.getTotal(),page.getPages(),page.getCurrent(),
                page.getSize(),records);
    }
}

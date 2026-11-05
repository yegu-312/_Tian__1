package com.example.fished.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.fished.entity.DoSample;
import com.example.fished.mapper.DoSampleMapper;
import com.example.fished.service.DoSampleService;
import com.example.fished.sim.DoPoint;
import com.example.fished.sim.PondSnapshot;
import com.example.fished.sim.SimAssembler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;


@Slf4j
@Service
public class DoSampleServiceImpl extends ServiceImpl<DoSampleMapper, DoSample>implements DoSampleService {

    @Override
    public int persist(Long pondId, List<DoPoint> points, PondSnapshot anchor) {
        if(points==null||points.isEmpty()){
            return 0;
        }
        List<DoSample> rows=points.stream().map(p->toEntity(pondId,p,anchor)).toList();
        saveBatch(rows);
        return rows.size();
    }
    private static DoSample toEntity(Long pondId,DoPoint point,PondSnapshot anchor){
        DoSample row = new DoSample();
        row.setPondId(pondId);

        row.setPondTime(SimAssembler.absoluteTime(anchor,point));
        row.setDoValue(BigDecimal.valueOf(point.doValue()));
        row.setTemperature(BigDecimal.valueOf(point.temperature()));
        row.setAeratorOn(point.aeratorOn());
        return row;
    }
}

package com.example.fished.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.fished.entity.DoSample;
import com.example.fished.sim.DoPoint;
import com.example.fished.sim.PondSnapshot;

import java.util.List;

public interface DoSampleService extends IService<DoSample>{
    int persist(Long pondId, List<DoPoint> points, PondSnapshot anchor);
}

package com.example.fished.sim;

import com.example.fished.common.BusinessException;
import com.example.fished.entity.PondConfig;
import com.example.fished.mapper.AeratorLogMapper;
import com.example.fished.mapper.AeratorStrategyMapper;
import com.example.fished.mapper.AlertMapper;
import com.example.fished.mapper.DoSampleMapper;
import com.example.fished.mapper.PondConfigMapper;
import com.example.fished.mapper.SimSettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PondManagerTest {

    private PondConfigMapper pondConfigMapper;
    private AeratorStrategyMapper strategyMapper;
    private SimSettingMapper settingMapper;
    private AlertMapper alertMapper;
    private AeratorLogMapper aeratorLogMapper;
    private DoSampleMapper doSampleMapper;
    private PondManager pondManager;

    private PondConfig first;
    private PondConfig second;

    @BeforeEach
    void setUp() {
        pondConfigMapper = mock(PondConfigMapper.class);
        strategyMapper = mock(AeratorStrategyMapper.class);
        settingMapper = mock(SimSettingMapper.class);
        alertMapper = mock(AlertMapper.class);
        aeratorLogMapper = mock(AeratorLogMapper.class);
        doSampleMapper = mock(DoSampleMapper.class);

        first = config(1L, "一号塘");
        second = config(2L, "二号塘");
        when(pondConfigMapper.selectList(any())).thenReturn(List.of(first, second));
        when(pondConfigMapper.selectById(1L)).thenReturn(first);
        when(pondConfigMapper.selectById(2L)).thenReturn(second);
        when(strategyMapper.selectOne(any())).thenReturn(null);
        when(settingMapper.selectOne(any())).thenReturn(null);

        pondManager = new PondManager(pondConfigMapper, strategyMapper, settingMapper,
                alertMapper, aeratorLogMapper, doSampleMapper);
        pondManager.initialize();
    }

    @Test
    @DisplayName("删除鱼塘时清理关联数据并从运行中心移除")
    void deletePondRemovesRelatedRowsAndEngine() {
        assertEquals(List.of(1L, 2L), pondManager.pondIds());

        pondManager.deletePond(1L);

        assertEquals(List.of(2L), pondManager.pondIds());
        verify(alertMapper).delete(any());
        verify(aeratorLogMapper).delete(any());
        verify(doSampleMapper).delete(any());
        verify(strategyMapper).delete(any());
        verify(settingMapper).delete(any());
        verify(pondConfigMapper).deleteById(1L);
    }

    @Test
    @DisplayName("至少保留一个运行中的鱼塘")
    void deleteLastRunningPondIsRejected() {
        pondManager.deletePond(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> pondManager.deletePond(2L));
        assertTrue(e.getMessage().contains("至少保留"));
        verify(pondConfigMapper, never()).deleteById(2L);
    }

    @Test
    @DisplayName("不存在的鱼塘删除时给出明确提示")
    void deleteMissingPondIsRejected() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> pondManager.deletePond(99L));
        assertTrue(e.getMessage().contains("不存在"));
    }

    private static PondConfig config(Long id, String name) {
        PondConfig config = new PondConfig();
        config.setId(id);
        config.setPondName(name);
        return config;
    }
}

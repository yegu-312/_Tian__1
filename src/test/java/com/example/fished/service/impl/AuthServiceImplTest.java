package com.example.fished.service.impl;

import com.example.fished.common.BusinessException;
import com.example.fished.common.PasswordHasher;
import com.example.fished.entity.SysUser;
import com.example.fished.mapper.SysUserMapper;
import com.example.fished.vo.LoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {

    private SysUserMapper sysUserMapper;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);
        authService = new AuthServiceImpl(sysUserMapper);
        ReflectionTestUtils.setField(authService, "registrationEnabled", true);
    }

    @Test
    @DisplayName("注册成功：创建 operator 账号并只保存密码散列")
    void registerCreatesOperatorAccount() {
        when(sysUserMapper.selectCount(any())).thenReturn(0L);
        when(sysUserMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(9L);
            return 1;
        });

        LoginVO vo = authService.register("new_user", "  新用户  ", "secret1", "secret1");

        assertEquals(9L, vo.getId());
        assertEquals("new_user", vo.getUsername());
        assertEquals("新用户", vo.getDisplayName());
        assertEquals("operator", vo.getRole());

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        SysUser saved = captor.getValue();
        assertEquals("new_user", saved.getUsername());
        assertEquals("operator", saved.getRole());
        assertEquals(Integer.valueOf(1), saved.getEnabled());
        assertNotEquals("secret1", saved.getPasswordHash());
        assertTrue(PasswordHasher.matches("secret1", saved.getSalt(), saved.getPasswordHash()));
    }

    @Test
    @DisplayName("显示名称为空时使用用户名")
    void registerFallsBackToUsernameAsDisplayName() {
        when(sysUserMapper.selectCount(any())).thenReturn(0L);
        when(sysUserMapper.insert(any(SysUser.class))).thenReturn(1);

        LoginVO vo = authService.register("pond_keeper", "   ", "secret1", "secret1");

        assertEquals("pond_keeper", vo.getDisplayName());
    }

    @Test
    @DisplayName("用户名格式不合法时拒绝注册")
    void registerRejectsInvalidUsername() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.register("1", "", "secret1", "secret1"));
        assertTrue(e.getMessage().contains("3-32"));
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("两次密码不一致时拒绝注册")
    void registerRejectsPasswordMismatch() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.register("new_user", "", "secret1", "secret2"));
        assertTrue(e.getMessage().contains("不一致"));
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("用户名已存在时拒绝注册")
    void registerRejectsDuplicateUsername() {
        when(sysUserMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.register("admin", "", "secret1", "secret1"));
        assertTrue(e.getMessage().contains("已被注册"));
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("并发注册触发唯一索引时返回友好提示")
    void registerTranslatesDuplicateKeyException() {
        when(sysUserMapper.selectCount(any())).thenReturn(0L);
        when(sysUserMapper.insert(any(SysUser.class))).thenThrow(new DuplicateKeyException("duplicate"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.register("new_user", "", "secret1", "secret1"));
        assertTrue(e.getMessage().contains("已被注册"));
    }

    @Test
    @DisplayName("关闭注册开关后拒绝新账号")
    void registerRejectsWhenDisabled() {
        ReflectionTestUtils.setField(authService, "registrationEnabled", false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.register("new_user", "", "secret1", "secret1"));
        assertTrue(e.getMessage().contains("未开放注册"));
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }
}

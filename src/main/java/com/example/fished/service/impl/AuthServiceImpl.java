package com.example.fished.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.fished.common.BusinessException;
import com.example.fished.common.PasswordHasher;
import com.example.fished.common.ResultCodeEnum;
import com.example.fished.entity.SysUser;
import com.example.fished.mapper.SysUserMapper;
import com.example.fished.service.AuthService;
import com.example.fished.vo.LoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{2,31}$");

    private final SysUserMapper sysUserMapper;

    public AuthServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public LoginVO login(String username, String password) {
        String name = username == null ? "" : username.trim();
        if (!StringUtils.hasText(name) || password == null) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }

        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, name)
                .eq(SysUser::getEnabled, 1));

        // 不区分“用户不存在”和“密码错误”，避免账号枚举
        if (user == null || !PasswordHasher.matches(password, user.getSalt(), user.getPasswordHash())) {
            log.warn("登录失败：用户名或密码错误（username={}）", name);
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }

        log.info("用户登录成功：{}（{}）", user.getUsername(), user.getRole());
        return new LoginVO(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    @Override
    public LoginVO updateAccount(Long userId, String currentPassword, String newUsername, String newPassword) {
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getEnabled())) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED.getCode(), "登录状态已失效，请重新登录");
        }
        if (!PasswordHasher.matches(currentPassword, user.getSalt(), user.getPasswordHash())) {
            throw new BusinessException(ResultCodeEnum.FAIL.getCode(), "当前密码不正确");
        }

        String name = newUsername == null ? "" : newUsername.trim();
        boolean usernameChanged = StringUtils.hasText(name) && !name.equals(user.getUsername());
        boolean passwordChanged = StringUtils.hasText(newPassword);

        if (!usernameChanged && !passwordChanged) {
            throw new BusinessException(ResultCodeEnum.FAIL.getCode(), "账号名和新密码至少修改一项");
        }

        if (usernameChanged) {
            if (!USERNAME_PATTERN.matcher(name).matches()) {
                throw new BusinessException(ResultCodeEnum.FAIL.getCode(),
                        "账号名需为 3-32 位，以字母或数字开头，可包含字母、数字、下划线、点或中划线");
            }
            Long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, name)
                    .ne(SysUser::getId, user.getId()));
            if (exists != null && exists > 0) {
                throw new BusinessException(ResultCodeEnum.FAIL.getCode(), "该账号名已被使用");
            }
            user.setUsername(name);
        }

        if (passwordChanged) {
            if (newPassword.length() < 6 || newPassword.length() > 64) {
                throw new BusinessException(ResultCodeEnum.FAIL.getCode(), "新密码长度需为 6-64 位");
            }
            if (PasswordHasher.matches(newPassword, user.getSalt(), user.getPasswordHash())) {
                throw new BusinessException(ResultCodeEnum.FAIL.getCode(), "新密码不能与当前密码相同");
            }
            String salt = PasswordHasher.newSalt();
            user.setSalt(salt);
            user.setPasswordHash(PasswordHasher.hash(newPassword, salt));
        }

        sysUserMapper.updateById(user);
        log.info("账号信息已更新：id={}，用户名={}，修改用户名={}，修改密码={}",
                user.getId(), user.getUsername(), usernameChanged, passwordChanged);

        return new LoginVO(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}

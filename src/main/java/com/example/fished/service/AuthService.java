package com.example.fished.service;

import com.example.fished.vo.LoginVO;

public interface AuthService {

    /**
     * 校验用户名和密码，成功返回脱敏后的用户信息。
     */
    LoginVO login(String username, String password);

    /**
     * 注册新账号（角色固定为 operator），成功返回可直接登录的用户信息。
     */
    LoginVO register(String username, String displayName, String password, String confirmPassword);

    /**
     * 修改当前登录用户的账号名和 / 或密码，成功返回更新后的用户信息。
     */
    LoginVO updateAccount(Long userId, String currentPassword, String newUsername, String newPassword);
}

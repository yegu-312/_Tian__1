package com.example.fished.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册新账号。用户名与密码规则和登录 / 修改账号保持一致。
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 32, message = "用户名最多 32 个字符")
    private String username;

    @Size(max = 32, message = "显示名称最多 32 个字符")
    private String displayName;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需为 6-64 位")
    private String password;

    @NotBlank(message = "请再次输入密码")
    @Size(max = 64, message = "密码最多 64 个字符")
    private String confirmPassword;
}

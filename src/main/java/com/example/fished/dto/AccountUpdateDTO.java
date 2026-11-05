package com.example.fished.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改当前登录账号的账号名 / 密码，操作前必须验证当前密码。
 * newUsername、newPassword 留空表示不修改对应项。
 */
@Data
public class AccountUpdateDTO {

    @NotBlank(message = "当前密码不能为空")
    @Size(max = 64, message = "当前密码最多 64 个字符")
    private String currentPassword;

    @Size(max = 32, message = "账号名最多 32 个字符")
    private String newUsername;

    @Size(max = 64, message = "新密码最多 64 个字符")
    private String newPassword;
}

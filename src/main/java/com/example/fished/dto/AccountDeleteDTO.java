package com.example.fished.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 删除当前登录账号前，需要再次验证密码。 */
@Data
public class AccountDeleteDTO {

    @NotBlank(message = "请输入当前密码")
    @Size(max = 64, message = "当前密码最多 64 个字符")
    private String currentPassword;
}

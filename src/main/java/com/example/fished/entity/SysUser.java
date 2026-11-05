package com.example.fished.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.fished.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 登录用户，对应表 sys_user。
 * 密码只保存 PBKDF2 派生值和随机盐，不保存明文。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    private String passwordHash;

    private String salt;

    private String displayName;

    /** admin / operator */
    private String role;

    /** 1 启用，0 停用 */
    private Integer enabled;
}

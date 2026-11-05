package com.example.fished.config;

import com.example.fished.common.PasswordHasher;
import com.example.fished.entity.SysUser;
import com.example.fished.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用户表为空时创建默认管理员，账号密码可在 application.properties 中调整。
 * 只判断整张表是否为空，避免管理员改名后重启又被重新创建。
 */
@Component
@Slf4j
public class AuthUserBootstrap implements ApplicationRunner {

    private final SysUserMapper sysUserMapper;

    @Value("${app.auth.default-username:admin}")
    private String defaultUsername;

    @Value("${app.auth.default-password:123456}")
    private String defaultPassword;

    @Value("${app.auth.default-display-name:系统管理员}")
    private String defaultDisplayName;

    public AuthUserBootstrap(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = StringUtils.hasText(defaultUsername) ? defaultUsername.trim() : "admin";
        Long count = sysUserMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }

        String salt = PasswordHasher.newSalt();
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setSalt(salt);
        user.setPasswordHash(PasswordHasher.hash(
                StringUtils.hasText(defaultPassword) ? defaultPassword : "123456", salt));
        user.setDisplayName(StringUtils.hasText(defaultDisplayName) ? defaultDisplayName : "系统管理员");
        user.setRole("admin");
        user.setEnabled(1);
        sysUserMapper.insert(user);

        log.info("用户表为空，已创建默认登录账号：{}（初始密码见 app.auth.default-password）", username);
    }
}

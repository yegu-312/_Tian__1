package com.example.fished.controller;

import com.example.fished.common.BusinessException;
import com.example.fished.common.Result;
import com.example.fished.common.ResultCodeEnum;
import com.example.fished.dto.AccountUpdateDTO;
import com.example.fished.dto.LoginDTO;
import com.example.fished.dto.RegisterDTO;
import com.example.fished.service.AuthService;
import com.example.fished.vo.LoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 登录、当前用户、退出登录。
 * 登录态保存在 HttpSession 中，由 AuthInterceptor 统一拦截。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String SESSION_USER = "AQUAGUARD_SESSION_USER";
    private static final int SESSION_SECONDS = 2 * 60 * 60;

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        LoginVO user = authService.login(dto.getUsername(), dto.getPassword());
        startSession(request, user);
        return Result.success(user);
    }

    /**
     * 注册成功后直接建立登录会话，前端无需再登录一次。
     */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request) {
        LoginVO user = authService.register(
                dto.getUsername(), dto.getDisplayName(), dto.getPassword(), dto.getConfirmPassword());
        startSession(request, user);
        return Result.success(user);
    }

    private void startSession(HttpServletRequest request, LoginVO user) {
        // 登录成功后更换会话，避免会话固定攻击
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_USER, user);
        session.setMaxInactiveInterval(SESSION_SECONDS);
    }

    @GetMapping("/me")
    public Result<LoginVO> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(SESSION_USER);
        if (!(value instanceof LoginVO user)) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED.getCode(), "未登录或登录已过期");
        }
        return Result.success(user);
    }

    @PutMapping("/account")
    public Result<LoginVO> updateAccount(@Valid @RequestBody AccountUpdateDTO dto,
                                         HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(SESSION_USER);
        if (!(value instanceof LoginVO current)) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED.getCode(), "未登录或登录已过期");
        }

        LoginVO updated = authService.updateAccount(
                current.getId(), dto.getCurrentPassword(), dto.getNewUsername(), dto.getNewPassword());
        session.setAttribute(SESSION_USER, updated);

        return Result.success(updated);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Result.success();
    }
}

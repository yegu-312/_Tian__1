package com.example.fished.config;

import com.example.fished.common.Result;
import com.example.fished.common.ResultCodeEnum;
import com.example.fished.controller.AuthController;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 未登录时：
 * - /api/** 返回 HTTP 401 + JSON，前端统一跳转登录页；
 * - 首页请求直接重定向到 /login.html。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public AuthInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(AuthController.SESSION_USER) != null) {
            return true;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    Result.fail(ResultCodeEnum.UNAUTHORIZED.getCode(), "未登录或登录已过期，请重新登录"));
        } else {
            response.sendRedirect(request.getContextPath() + "/login.html");
        }
        return false;
    }
}

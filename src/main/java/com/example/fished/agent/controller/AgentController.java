package com.example.fished.agent.controller;

import com.example.fished.agent.dto.AgentActionRequest;
import com.example.fished.agent.dto.AgentChatRequest;
import com.example.fished.agent.service.AgentService;
import com.example.fished.agent.vo.AgentMessageVO;
import com.example.fished.agent.vo.AgentStatusVO;
import com.example.fished.common.BusinessException;
import com.example.fished.common.Result;
import com.example.fished.common.ResultCodeEnum;
import com.example.fished.controller.AuthController;
import com.example.fished.vo.LoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

/**
 * AI 助手接口。登录态沿用 AuthInterceptor；操作人只从会话读取。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private static final long SSE_TIMEOUT_MILLIS = 180_000L;

    private final AgentService agentService;
    private final ThreadPoolTaskExecutor agentExecutor;

    public AgentController(AgentService agentService,
                           @Qualifier("agentExecutor") ThreadPoolTaskExecutor agentExecutor) {
        this.agentService = agentService;
        this.agentExecutor = agentExecutor;
    }

    @GetMapping("/status")
    public Result<AgentStatusVO> status() {
        return Result.success(agentService.status());
    }

    @GetMapping("/history")
    public Result<List<AgentMessageVO>> history(
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        return Result.success(agentService.history(sessionId(request), limit));
    }

    @PostMapping("/reset")
    public Result<Void> reset(HttpServletRequest request) {
        agentService.reset(sessionId(request));
        return Result.success();
    }

    @PostMapping("/reject")
    public Result<Void> reject(@Valid @RequestBody AgentActionRequest dto, HttpServletRequest request) {
        agentService.reject(sessionId(request), dto.getActionId());
        return Result.success();
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AgentChatRequest dto,
                           HttpServletRequest request,
                           HttpServletResponse response) {
        LoginVO user = requireUser(request);
        SseEmitter emitter = createEmitter(response);
        String sessionId = sessionId(request);
        submit(emitter, () -> agentService.chat(sessionId, user.getUsername(),
                dto.getMessage(), dto.getPondId(), emitter));
        return emitter;
    }

    @PostMapping(value = "/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter confirm(@Valid @RequestBody AgentActionRequest dto,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        LoginVO user = requireUser(request);
        SseEmitter emitter = createEmitter(response);
        String sessionId = sessionId(request);
        submit(emitter, () -> agentService.confirm(sessionId, user.getUsername(),
                dto.getActionId(), emitter));
        return emitter;
    }

    // ------------------------------------------------------------------

    private void submit(SseEmitter emitter, Runnable task) {
        try {
            agentExecutor.execute(task);
        } catch (RuntimeException e) {
            log.warn("Agent 线程池繁忙：{}", e.getMessage());
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data("{\"message\":\"助手当前繁忙，请稍后再试\"}", MediaType.TEXT_PLAIN));
            } catch (IOException ignored) {
                // 客户端已断开
            }
            emitter.complete();
        }
    }

    private static SseEmitter createEmitter(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return new SseEmitter(SSE_TIMEOUT_MILLIS);
    }

    private static String sessionId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED.getCode(), "未登录或登录已过期");
        }
        return session.getId();
    }

    private static LoginVO requireUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(AuthController.SESSION_USER);
        if (!(value instanceof LoginVO user)) {
            throw new BusinessException(ResultCodeEnum.UNAUTHORIZED.getCode(), "未登录或登录已过期");
        }
        return user;
    }
}

package com.example.fished.agent.service;

import com.example.fished.agent.vo.AgentPendingActionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 包装 SseEmitter：客户端断开后静默忽略后续事件。
 */
public class AgentEventWriter {

    private static final Logger log = LoggerFactory.getLogger(AgentEventWriter.class);

    private final SseEmitter emitter;
    private final ObjectMapper mapper;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public AgentEventWriter(SseEmitter emitter, ObjectMapper mapper) {
        this.emitter = emitter;
        this.mapper = mapper;
    }

    public void text(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", text);
        send("text", data);
    }

    public void toolStart(String name, String label) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("label", label);
        data.put("status", "start");
        send("tool", data);
    }

    public void toolDone(String name, String label, boolean ok, String summary) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("label", label);
        data.put("status", "done");
        data.put("ok", ok);
        data.put("summary", summary == null ? "" : summary);
        send("tool", data);
    }

    public void pending(AgentPendingActionVO action) {
        send("pending_action", action);
    }

    public void done(String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reason", reason);
        send("done", data);
    }

    public void error(String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", message == null ? "AI 助手暂时不可用" : message);
        send("error", data);
    }

    public boolean isClosed() {
        return closed.get();
    }

    public void complete() {
        if (closed.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SSE 已结束：{}", e.getMessage());
            }
        }
    }

    private void send(String event, Object data) {
        if (closed.get()) {
            return;
        }
        try {
            String payload = data instanceof String ? (String) data : mapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(event).data(payload, MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            // 客户端已断开时 send 会抛异常，停止后续输出即可。
            closed.set(true);
            log.debug("SSE 发送失败（客户端可能已断开）：{}", e.getMessage());
        }
    }
}

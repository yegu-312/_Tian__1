package com.example.fished.agent.tool;

/**
 * 一次工具调用可用的上下文。操作人必须来自登录会话，不能由模型提供。
 */
public record ToolContext(String sessionId, String operator, Long activePondId) {
}

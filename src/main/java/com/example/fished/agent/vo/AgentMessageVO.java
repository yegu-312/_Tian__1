package com.example.fished.agent.vo;

/**
 * 返回给前端恢复对话历史的消息（只包含用户和助手文本）。
 */
public record AgentMessageVO(String role, String content) {
}

package com.example.fished.agent.vo;

/**
 * 前端据此判断助手是否可用；不暴露 API Key。
 */
public record AgentStatusVO(boolean enabled, boolean configured, String provider, String model) {
}

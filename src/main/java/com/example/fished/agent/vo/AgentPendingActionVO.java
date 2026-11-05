package com.example.fished.agent.vo;

import com.example.fished.agent.service.PendingAction;

/**
 * 待用户确认的写操作。
 */
public record AgentPendingActionVO(String id, String tool, String title,
                                   String description, String expiresAt) {

    public static AgentPendingActionVO from(PendingAction action) {
        return new AgentPendingActionVO(
                action.id(),
                action.toolName(),
                action.title(),
                action.description(),
                action.expiresAt().toString());
    }
}

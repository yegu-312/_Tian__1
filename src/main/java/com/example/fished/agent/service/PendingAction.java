package com.example.fished.agent.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * 等待用户确认的写操作。参数在创建时冻结，确认时原样执行。
 */
public record PendingAction(String id,
                            String sessionId,
                            String toolName,
                            JsonNode args,
                            String toolCallId,
                            Long activePondId,
                            String title,
                            String description,
                            Instant expiresAt) {
}

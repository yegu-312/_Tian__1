package com.example.fished.agent.llm;

/**
 * Agent 调用大模型时的可展示异常。
 */
public class AgentException extends RuntimeException {

    public AgentException(String message) {
        super(message);
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}

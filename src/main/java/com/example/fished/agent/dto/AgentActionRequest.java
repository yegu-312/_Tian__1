package com.example.fished.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 确认/拒绝待执行操作。只传服务端生成的不透明 actionId，不传任何工具参数。
 */
public class AgentActionRequest {

    @NotBlank(message = "actionId 不能为空")
    private String actionId;

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }
}

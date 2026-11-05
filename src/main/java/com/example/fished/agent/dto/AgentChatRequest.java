package com.example.fished.agent.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 聊天请求。pondId 由前端根据当前选中鱼塘传入，仅作为上下文提示。
 */
public class AgentChatRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题最多 2000 字")
    private String message;

    @Min(value = 1, message = "pondId 不合法")
    private Long pondId;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getPondId() {
        return pondId;
    }

    public void setPondId(Long pondId) {
        this.pondId = pondId;
    }
}

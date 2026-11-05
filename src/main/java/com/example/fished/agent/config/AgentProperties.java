package com.example.fished.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 助手配置。Key 只从环境变量或本地配置读取，不写入代码。
 */
@Data
@ConfigurationProperties(prefix = "app.agent")
public class AgentProperties {

    private boolean enabled = true;
    private String provider = "deepseek";
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-chat";
    private int maxToolRounds = 6;
    private int maxToolsPerRound = 4;
    private int maxHistoryMessages = 30;
    private int requestTimeoutSeconds = 120;
    private int pendingActionTtlSeconds = 300;
    private int maxTokens = 1200;
    private double temperature = 0.3;

    /** stream=false 时退化为非流式请求，便于兼容异常代理。 */
    private boolean stream = true;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}

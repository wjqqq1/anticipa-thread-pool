package com.baomihuahua.anticipa.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "anticipa.agent")
public class AgentProperties {
    private boolean enabled = true;
    private int maxIterations = 15;
    private String model = "gpt-4";
    private String apiKey;
    private String baseUrl = "https://api.openai.com";
    private String apiToken;
}

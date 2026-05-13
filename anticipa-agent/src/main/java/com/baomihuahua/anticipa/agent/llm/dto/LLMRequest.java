package com.baomihuahua.anticipa.agent.llm.dto;

import com.baomihuahua.anticipa.agent.AgentLoop;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LLMRequest {
    private List<AgentLoop.Message> messages;
    private double temperature = 0.7;
    private Integer maxTokens;

    public LLMRequest(List<AgentLoop.Message> messages) {
        this.messages = messages;
        this.temperature = 0.7;
    }
}

package com.baomihuahua.anticipa.agent.llm.dto;

import com.baomihuahua.anticipa.agent.AgentLoop;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LLMRequest {
    private List<AgentLoop.Message> messages;
}

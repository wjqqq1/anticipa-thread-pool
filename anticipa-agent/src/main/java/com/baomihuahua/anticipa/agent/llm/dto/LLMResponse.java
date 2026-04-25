package com.baomihuahua.anticipa.agent.llm.dto;

import com.baomihuahua.anticipa.agent.AgentLoop;
import lombok.Data;

@Data
public class LLMResponse {
    private String content;
    private String thinking;
    private boolean hasToolCall;
    private AgentLoop.ToolCall toolCall;

    public static LLMResponse withContent(String content, String thinking) {
        LLMResponse r = new LLMResponse();
        r.content = content;
        r.thinking = thinking;
        r.hasToolCall = false;
        return r;
    }

    public static LLMResponse withToolCall(AgentLoop.ToolCall toolCall, String rawContent) {
        LLMResponse r = new LLMResponse();
        r.toolCall = toolCall;
        r.content = rawContent;
        r.hasToolCall = true;
        return r;
    }

    public boolean hasToolCall() {
        return hasToolCall;
    }
}

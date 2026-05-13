package com.baomihuahua.anticipa.agent.llm.dto;

import com.baomihuahua.anticipa.agent.AgentLoop;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class LLMResponse {
    private String content;
    private String thinking;
    private boolean hasToolCall;
    private AgentLoop.ToolCall toolCall;

    /** 原生 Function Calling 支持：一次返回多个工具调用 */
    private List<AgentLoop.ToolCall> toolCalls;

    /** 完成原因：stop / length / tool_calls / error */
    private String finishReason;

    private int promptTokens;
    private int completionTokens;

    public static LLMResponse withContent(String content, String thinking) {
        LLMResponse r = new LLMResponse();
        r.content = content;
        r.thinking = thinking;
        r.hasToolCall = false;
        r.finishReason = "stop";
        return r;
    }

    /**
     * 传输层失败或 HTTP 非成功等「非模型正常输出」场景；调用方应重试或向用户报错，勿当 JSON 解析。
     */
    public static LLMResponse withError(String content, String thinking) {
        LLMResponse r = new LLMResponse();
        r.content = content;
        r.thinking = thinking;
        r.hasToolCall = false;
        r.finishReason = "error";
        return r;
    }

    public boolean isErrorResponse() {
        return "error".equals(finishReason);
    }

    public static LLMResponse withToolCall(AgentLoop.ToolCall toolCall, String rawContent) {
        // 文本回退路径可能没有 id，生成合成 ID
        if (toolCall != null && (toolCall.getId() == null || toolCall.getId().isEmpty())) {
            toolCall.setId("fallback_" + UUID.randomUUID().toString().substring(0, 8));
        }
        LLMResponse r = new LLMResponse();
        r.toolCall = toolCall;
        r.toolCalls = toolCall != null ? List.of(toolCall) : null;
        r.content = rawContent;
        r.hasToolCall = true;
        r.finishReason = "tool_calls";
        return r;
    }

    public static LLMResponse withToolCalls(List<AgentLoop.ToolCall> toolCalls) {
        // 确保每个 toolCall 都有 id
        if (toolCalls != null) {
            for (AgentLoop.ToolCall tc : toolCalls) {
                if (tc.getId() == null || tc.getId().isEmpty()) {
                    tc.setId("fallback_" + UUID.randomUUID().toString().substring(0, 8));
                }
            }
        }
        LLMResponse r = new LLMResponse();
        r.toolCalls = toolCalls;
        r.hasToolCall = toolCalls != null && !toolCalls.isEmpty();
        r.toolCall = r.hasToolCall ? toolCalls.get(0) : null;
        r.finishReason = "tool_calls";
        return r;
    }

    public boolean hasToolCall() {
        return hasToolCall;
    }
}

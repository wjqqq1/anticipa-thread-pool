package com.baomihuahua.anticipa.agent.llm;

import com.baomihuahua.anticipa.agent.ToolDefinition;
import com.baomihuahua.anticipa.agent.llm.dto.LLMRequest;
import com.baomihuahua.anticipa.agent.llm.dto.LLMResponse;

import java.util.List;
import java.util.function.Consumer;

public interface AIClient {
    /** 基础聊天（无工具调用） */
    LLMResponse chat(LLMRequest request);

    /**
     * 带原生 Function Calling 的聊天。
     *
     * @param request   LLM 请求
     * @param tools     工具定义列表（转换为 JSON Schema 传递）
     * @return LLM 响应（可能包含 tool_calls）
     */
    LLMResponse chatWithTools(LLMRequest request, List<ToolDefinition> tools);

    /**
     * 流式聊天（SSE）。
     *
     * @param request    LLM 请求
     * @param tools      工具定义列表（可为 null）
     * @param onEvent    事件回调（type + payload）
     * @param onComplete 完成回调（最终响应）
     */
    void chatStream(LLMRequest request, List<ToolDefinition> tools,
                    Consumer<StreamEvent> onEvent, Consumer<LLMResponse> onComplete);

    /** SSE 流式事件 */
    class StreamEvent {
        private final String type;
        private final String payload;

        public StreamEvent(String type, String payload) {
            this.type = type;
            this.payload = payload;
        }

        public String getType() { return type; }
        public String getPayload() { return payload; }
    }
}

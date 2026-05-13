package com.baomihuahua.anticipa.agent.event;

/**
 * SSE 事件类型常量，定义前端可消费的各类 SSE event name。
 * 每个常量对应一个 SSE 事件，其 data 字段为对应事件的 JSON 载荷。
 */
public final class PipelineEvent {

    // ─── Stage 1: Pre-Request ───
    public static final String KNOWLEDGE_RETRIEVED = "knowledge_retrieved";

    // ─── Stage 2: API Call（流式增量） ───
    public static final String THINKING = "thinking";
    public static final String ANSWER = "answer";

    // ─── Stage 3/4: Tool Execution ───
    public static final String TOOL_CALL = "tool_call";
    public static final String TOOL_RESULT = "tool_result";

    // ─── 审批 ───
    public static final String APPROVAL_REQUIRED = "approval_required";

    // ─── 进度 ───
    public static final String PROGRESS = "progress";

    // ─── 终态 ───
    public static final String DONE = "done";
    public static final String ERROR = "error";

    private PipelineEvent() {}
}

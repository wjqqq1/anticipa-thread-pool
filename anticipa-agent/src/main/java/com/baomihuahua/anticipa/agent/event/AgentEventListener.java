package com.baomihuahua.anticipa.agent.event;

import com.baomihuahua.anticipa.agent.ToolResult;
import com.baomihuahua.anticipa.agent.approval.ApprovalRequest;

import java.util.Map;

/**
 * Agent 流水线事件监听器。
 * <p>
 * 非流式模式下使用 {@link #noop()}（空实现），流式模式下注入 SSE 转发实现。
 * 所有方法提供 default 空实现，实现类只需覆写关心的方法。
 * </p>
 */
public interface AgentEventListener {

    // ─── Stage 1: Pre-Request ───

    default void onKnowledgeRetrieved(String summary) {}

    // ─── Stage 2: API Call（LLM 流式增量 — 核心） ───

    default void onThinkingDelta(String delta) {}

    default void onAnswerDelta(String delta) {}

    // ─── Stage 3/4: Tool Execution ───

    default void onToolCallDetected(String toolName, Map<String, Object> params) {}

    default void onToolExecutionStart(String toolName) {}

    default void onToolExecutionResult(String toolName, ToolResult result) {}

    default void onApprovalRequired(ApprovalRequest request) {}

    // ─── 进度 ───

    default void onTurnProgress(int turn, int maxTurns, int tokens) {}

    // ─── 终态 ───

    default void onComplete(String content, String thinking) {}

    default void onError(String error) {}

    // ─── Noop 空实现（用于非流式场景） ───

    static AgentEventListener noop() {
        return new AgentEventListener() {};
    }
}

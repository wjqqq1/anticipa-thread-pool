package com.baomihuahua.anticipa.agent;

import com.baomihuahua.anticipa.agent.approval.*;
import com.baomihuahua.anticipa.agent.audit.AuditStore;
import com.baomihuahua.anticipa.agent.context.ContextManager;
import com.baomihuahua.anticipa.agent.event.AgentEventListener;
import com.baomihuahua.anticipa.agent.event.ApprovalFutureManager;
import com.baomihuahua.anticipa.agent.knowledge.KnowledgeDocument;
import com.baomihuahua.anticipa.agent.knowledge.KnowledgeRetriever;
import com.baomihuahua.anticipa.agent.knowledge.KnowledgeService;
import com.baomihuahua.anticipa.agent.llm.AIClient;
import com.baomihuahua.anticipa.agent.llm.dto.LLMRequest;
import com.baomihuahua.anticipa.agent.llm.dto.LLMResponse;
import com.baomihuahua.anticipa.agent.memory.MemoryManager;
import com.baomihuahua.anticipa.agent.permission.PermissionManager;
import com.baomihuahua.anticipa.agent.prompt.SystemPromptBuilder;
import com.baomihuahua.anticipa.agent.tool.ToolSearchService;
import com.baomihuahua.anticipa.core.monitor.ThreadPoolLogRecord;
import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Agent 核心引擎。
 * <p>
 * 参考 Claude Code 六阶段循环设计：
 * 1. Pre-Request（前置处理：上下文压缩、记忆注入、知识增强）
 * 2. API Call（LLM 调用 + 原生 Function Calling + 重试）
 * 3. Response Processing（响应处理：判断 end_turn / tool_use）
 * 4. Tool Execution（工具执行 + Deny-First 权限检查 + 并发控制）
 * 5. Post-Turn（后置处理：审计、记忆更新、衰减检测）
 * 6. Loop Control（循环控制：最大轮次、Token 预算、错误恢复）
 * </p>
 */
@Slf4j
@Service
public class AgentLoop {

    private final AIClient aiClient;
    private final ToolRegistry toolRegistry;
    private final ToolSearchService toolSearchService;
    private final PermissionManager permissionManager;
    private final ApprovalService approvalService;
    private final AuditStore auditStore;
    private final ContextManager contextManager;
    private final MemoryManager memoryManager;
    private final KnowledgeService knowledgeService;
    private final SystemPromptBuilder promptBuilder;
    private final ApprovalFutureManager approvalFutureManager;

    private static final int MAX_TURNS = 15;
    private static final int DIMINISHING_THRESHOLD = 3;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    public AgentLoop(AIClient aiClient, ToolRegistry toolRegistry,
                     ToolSearchService toolSearchService,
                     PermissionManager permissionManager,
                     ApprovalService approvalService, AuditStore auditStore,
                     ContextManager contextManager, MemoryManager memoryManager,
                     KnowledgeService knowledgeService, SystemPromptBuilder promptBuilder,
                     ApprovalFutureManager approvalFutureManager) {
        this.aiClient = aiClient;
        this.toolRegistry = toolRegistry;
        this.toolSearchService = toolSearchService;
        this.permissionManager = permissionManager;
        this.approvalService = approvalService;
        this.auditStore = auditStore;
        this.contextManager = contextManager;
        this.memoryManager = memoryManager;
        this.knowledgeService = knowledgeService;
        this.promptBuilder = promptBuilder;
        this.approvalFutureManager = approvalFutureManager;
    }

    // ─── 对外接口 ───

    /**
     * 非流式处理入口，内部委托给 {@link #processStream} + noop 监听器。
     */
    public AgentResponse process(AgentRequest request) {
        return processStream(request, AgentEventListener.noop());
    }

    /**
     * 流式处理入口，走完整的 6 阶段流水线，通过 listener 实时推送中间状态。
     */
    public AgentResponse processStream(AgentRequest request, AgentEventListener listener) {
        QueryParams params = new QueryParams(
                request.getSessionId(),
                request.getUserId(),
                request.getUserMessage(),
                request.getSessionContext());
        return executeStream(params, request.getHistory(), listener);
    }

    public AgentResponse continueAfterApproval(ApprovalRequest approvalReq, ApprovalDecision decision) {
        if (decision.isRejected()) {
            return AgentResponse.finalAnswer("操作已被取消。" + decision.getReason(), null);
        }
        ToolDefinition tool = toolRegistry.getTool(approvalReq.getToolName());
        if (tool == null) {
            return AgentResponse.finalAnswer("工具 " + approvalReq.getToolName() + " 不存在", null);
        }
        Map<String, Object> params = decision.getModifiedParams() != null
                ? decision.getModifiedParams() : approvalReq.getParams();
        ToolResult result = tool.getExecutor().apply(params);
        return AgentResponse.finalAnswer(result.getSummary(), null);
    }

    public AgentReport executeSilent(SilentRequest request) {
        String systemPrompt = buildSilentSystemPrompt(request);
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt));
        messages.add(new Message("user", buildSilentUserPrompt(request)));

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                LLMRequest llmReq = new LLMRequest(messages);
                llmReq.setTemperature(0.3);
                LLMResponse llmResponse = aiClient.chat(llmReq);
                if (llmResponse == null) {
                    throw new IOException("LLM 返回空响应");
                }
                if (llmResponse.isErrorResponse()) {
                    throw new IOException(llmResponse.getContent() != null ? llmResponse.getContent() : "LLM 错误");
                }
                AgentReport report = parseAnalysisReport(llmResponse);

                if (request.isAutoAdjust() && report.isAdjustmentRecommended()
                        && report.getSuggestedAdjustments() != null && !report.getSuggestedAdjustments().isEmpty()) {
                    ToolDefinition adjustTool = toolRegistry.getTool("adjust_thread_pool");
                    if (adjustTool != null) {
                        Map<String, Object> p = new HashMap<>();
                        p.put("thread_pool_id", request.getThreadPoolId());
                        p.putAll(report.getSuggestedAdjustments());
                        ToolResult result = adjustTool.getExecutor().apply(p);
                        report.setAdjustmentApplied(true);
                        report.setAdjustmentResult(result.getSummary());
                    }
                }
                return report;
            } catch (Exception e) {
                log.warn("[AgentLoop] silent execute attempt {}/{} failed: {}", attempt + 1, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES - 1) {
                    sleepWithBackoff(attempt);
                } else {
                    AgentReport errorReport = new AgentReport();
                    errorReport.setSummary("分析执行失败");
                    errorReport.setAnalysis("错误: " + e.getMessage());
                    errorReport.setHealthStatus("UNKNOWN");
                    return errorReport;
                }
            }
        }
        return null;
    }

    // ─── 6 阶段流水线核心 ───

    /**
     * 流式流水线：基于 execute() 在关键节点插入 listener 调用。
     * 原有 execute() 保留不动，非流式 process() 走 processStream + noop。
     */
    private AgentResponse executeStream(QueryParams params, List<Message> history,
                                         AgentEventListener listener) {
        log.info("[AgentLoop] 流式流水线启动: sessionId={}, userId={}, message={}",
                params.sessionId(), params.userId(),
                params.userMessage() != null ? params.userMessage().substring(0, Math.min(60, params.userMessage().length())) : "null");

        // 统一走标准 Agent 路径：知识检索 + 记忆检索 + 工具检索 + Agent 循环
        AgentState state = new AgentState();
        state.setMessages(new ArrayList<>());
        state.getMessages().add(new Message("system", ""));
        if (history != null) {
            state.getMessages().addAll(history);
        }
        state.getMessages().add(new Message("user", params.userMessage()));
        state.setTurnCount(0);
        state.setDiminishingCount(0);
        state.setRetryCount(0);
        state.setLastToolResult(null);

        List<ToolDefinition> tools = toolSearchService.search(params.userMessage());
        log.info("[AgentLoop] 工具检索完成: 匹配到 {} 个工具: {}",
                tools.size(),
                tools.stream().map(ToolDefinition::getName).collect(Collectors.toList()));
        String knowledgeText = retrieveKnowledge(params);
        if (knowledgeText != null) {
            listener.onKnowledgeRetrieved("知识检索完成");
            log.info("[AgentLoop] 知识检索完成: textLength={}", knowledgeText.length());
        } else {
            log.info("[AgentLoop] 知识检索: 无匹配内容");
        }
        String memoryText = retrieveMemory(params);

        // 构建 System Prompt
        String systemPrompt = buildSystemPrompt(params, tools, knowledgeText, memoryText);
        state.getMessages().set(0, new Message("system", systemPrompt));

        while (state.getTurnCount() < MAX_TURNS) {
            state.setTurnCount(state.getTurnCount() + 1);
            listener.onTurnProgress(state.getTurnCount(), MAX_TURNS, state.getTotalTokensUsed());
            log.debug("[AgentLoop] Turn {}/{} start, messages={}", state.getTurnCount(), MAX_TURNS, state.getMessages().size());

            try {
                // Stage 1: Pre-Request
                state = preRequest(state, params);

                // Stage 2: API Call（流式）
                log.info("[AgentLoop] Turn {}/{}: 开始流式API调用, messages={}", state.getTurnCount(), MAX_TURNS, state.getMessages().size());
                LLMResponse llmResponse = apiCallStream(state, tools, listener);
                if (llmResponse == null) {
                    log.warn("[AgentLoop] Turn {}/{}: API调用返回null", state.getTurnCount(), MAX_TURNS);
                    listener.onError("AI 服务暂时不可用");
                    return AgentResponse.finalAnswer("AI 服务暂时不可用，请稍后重试。", null);
                }

                // Stage 3: Response Processing
                if (!llmResponse.hasToolCall()) {
                    String thinking = llmResponse.getThinking();
                    String content = llmResponse.getContent();
                    log.info("[AgentLoop] Turn {}/{}: LLM返回纯文本回答, contentLength={}", state.getTurnCount(), MAX_TURNS, content != null ? content.length() : 0);
                    postProcess(state, params, llmResponse, null);
                    listener.onComplete(content, thinking);
                    return AgentResponse.finalAnswer(content, thinking);
                }

                // Stage 4: Tool Execution（流式）
                log.info("[AgentLoop] Turn {}/{}: LLM请求工具调用", state.getTurnCount(), MAX_TURNS);

                // 添加 assistant 消息（包含 tool_calls）
                Message assistantMsg = new Message("assistant", llmResponse.getContent());
                assistantMsg.setToolCalls(llmResponse.getToolCalls());
                state.getMessages().add(assistantMsg);

                ToolExecutionResult toolResult = executeToolsStream(llmResponse, params, state, listener);
                if (toolResult.isWaitingApproval()) {
                    // 流式模式下审批由 ApprovalFutureManager 阻塞等待，走到这里说明超时
                    return AgentResponse.waitingApproval(llmResponse.getThinking(), toolResult.getApprovalRequest());
                }

                // 添加所有 tool 消息（含错误消息，已携带 toolCallId）
                for (Message toolMsg : toolResult.getToolMessages()) {
                    state.getMessages().add(toolMsg);
                }
                if (!toolResult.getResults().isEmpty()) {
                    state.setLastToolResult(toolResult.getResults().get(
                            toolResult.getResults().size() - 1).toJson());
                }

                // Stage 5: Post-Turn
                postProcess(state, params, llmResponse, toolResult);

                // Stage 6: Loop Control
                if (shouldStop(state)) {
                    break;
                }

            } catch (Exception e) {
                log.error("[AgentLoop] Turn {} error: {}", state.getTurnCount(), e.getMessage(), e);
                listener.onError(e.getMessage());
                if (!handleError(state, e)) {
                    return AgentResponse.finalAnswer(
                            "处理过程中出现错误，请重试或简化您的请求。", "error: " + e.getMessage());
                }
            }
        }

        String finalContent = "已分析 " + state.getTurnCount() + " 轮，当前结论如上。如需更深入分析，请提供更多信息。";
        String finalThinking = "reach max turns or stopped by loop control";
        listener.onComplete(finalContent, finalThinking);
        return AgentResponse.finalAnswer(finalContent, finalThinking);
    }

    /**
     * 原有同步流水线，保留不动供参考。
     */
    private AgentResponse execute(QueryParams params, List<Message> history) {
        AgentState state = new AgentState();
        state.setMessages(new ArrayList<>());
        state.getMessages().add(new Message("system", ""));
        if (history != null) {
            state.getMessages().addAll(history);
        }
        state.getMessages().add(new Message("user", params.userMessage()));
        state.setTurnCount(0);
        state.setDiminishingCount(0);
        state.setRetryCount(0);
        state.setLastToolResult(null);

        // 工具检索 + 知识检索 + 记忆检索
        List<ToolDefinition> tools = toolSearchService.search(params.userMessage());
        String knowledgeText = retrieveKnowledge(params);
        String memoryText = retrieveMemory(params);

        // 构建 System Prompt
        String systemPrompt = buildSystemPrompt(params, tools, knowledgeText, memoryText);
        state.getMessages().set(0, new Message("system", systemPrompt));

        while (state.getTurnCount() < MAX_TURNS) {
            state.setTurnCount(state.getTurnCount() + 1);
            log.debug("[AgentLoop] Turn {}/{} start, messages={}", state.getTurnCount(), MAX_TURNS, state.getMessages().size());

            try {
                // Stage 1: Pre-Request
                state = preRequest(state, params);

                // Stage 2: API Call
                LLMResponse llmResponse = apiCall(state, tools);
                if (llmResponse == null) {
                    return AgentResponse.finalAnswer("AI 服务暂时不可用，请稍后重试。", null);
                }

                // Stage 3: Response Processing
                if (!llmResponse.hasToolCall()) {
                    String thinking = llmResponse.getThinking();
                    String content = llmResponse.getContent();
                    postProcess(state, params, llmResponse, null);
                    return AgentResponse.finalAnswer(content, thinking);
                }

                // Stage 4: Tool Execution
                // 添加 assistant 消息（包含 tool_calls）
                Message assistantMsg = new Message("assistant", llmResponse.getContent());
                assistantMsg.setToolCalls(llmResponse.getToolCalls());
                state.getMessages().add(assistantMsg);

                ToolExecutionResult toolResult = executeTools(llmResponse, params, state);
                if (toolResult.isWaitingApproval()) {
                    return AgentResponse.waitingApproval(llmResponse.getThinking(), toolResult.getApprovalRequest());
                }

                // 添加所有 tool 消息（含错误消息，已携带 toolCallId）
                for (Message toolMsg : toolResult.getToolMessages()) {
                    state.getMessages().add(toolMsg);
                }
                if (!toolResult.getResults().isEmpty()) {
                    state.setLastToolResult(toolResult.getResults().get(
                            toolResult.getResults().size() - 1).toJson());
                }

                // Stage 5: Post-Turn
                postProcess(state, params, llmResponse, toolResult);

                // Stage 6: Loop Control
                if (shouldStop(state)) {
                    break;
                }

            } catch (Exception e) {
                log.error("[AgentLoop] Turn {} error: {}", state.getTurnCount(), e.getMessage(), e);
                if (!handleError(state, e)) {
                    return AgentResponse.finalAnswer(
                            "处理过程中出现错误，请重试或简化您的请求。", "error: " + e.getMessage());
                }
            }
        }

        return AgentResponse.finalAnswer(
                "已分析 " + state.getTurnCount() + " 轮，当前结论如上。如需更深入分析，请提供更多信息。",
                "reach max turns or stopped by loop control");
    }

    // ─── Stage 1: Pre-Request ───

    private AgentState preRequest(AgentState state, QueryParams params) {
        if (contextManager != null) {
            List<Message> compressed = contextManager.prepareMessages(state.getMessages());
            state.setMessages(compressed);
        }
        return state;
    }

    // ─── Stage 2: API Call ───

    private LLMResponse apiCall(AgentState state, List<ToolDefinition> tools) {
        RuntimeException lastException = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                LLMRequest llmReq = new LLMRequest(state.getMessages());
                llmReq.setTemperature(0.7);

                LLMResponse response;
                if (tools != null && !tools.isEmpty()) {
                    response = aiClient.chatWithTools(llmReq, tools);
                } else {
                    response = aiClient.chat(llmReq);
                }

                if (response == null || response.isErrorResponse()) {
                    if (response != null && response.isErrorResponse()) {
                        throw new IOException(response.getContent() != null ? response.getContent() : "LLM 错误");
                    }
                    throw new IOException("LLM 返回空响应");
                }

                state.setTotalTokensUsed(state.getTotalTokensUsed()
                        + response.getPromptTokens() + response.getCompletionTokens());
                state.setRetryCount(0);
                return response;

            } catch (Exception e) {
                lastException = new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
                log.warn("[AgentLoop] API call attempt {}/{} failed: {}", attempt + 1, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES - 1) {
                    sleepWithBackoff(attempt);
                }
            }
        }

        log.error("[AgentLoop] API call exhausted all {} retries", MAX_RETRIES);
        return null;
    }

    // ─── Stage 2: API Call（流式） ───

    private LLMResponse apiCallStream(AgentState state, List<ToolDefinition> tools,
                                       AgentEventListener listener) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                LLMRequest llmReq = new LLMRequest(state.getMessages());
                llmReq.setTemperature(0.7);

                CompletableFuture<LLMResponse> future = new CompletableFuture<>();

                if (tools != null && !tools.isEmpty()) {
                    aiClient.chatStream(llmReq, tools,
                            event -> {
                                switch (event.getType()) {
                                    case "thinking" -> listener.onThinkingDelta(event.getPayload());
                                    case "answer"   -> listener.onAnswerDelta(event.getPayload());
                                }
                            },
                            response -> future.complete(response));
                } else {
                    aiClient.chatStream(llmReq, null,
                            event -> {
                                if ("thinking".equals(event.getType())) {
                                    listener.onThinkingDelta(event.getPayload());
                                } else if ("answer".equals(event.getType())) {
                                    listener.onAnswerDelta(event.getPayload());
                                }
                            },
                            response -> future.complete(response));
                }

                LLMResponse response = future.get(5, TimeUnit.MINUTES);
                if (response == null || response.isErrorResponse()) {
                    if (response != null && response.isErrorResponse()) {
                        throw new IOException(response.getContent() != null ? response.getContent() : "LLM 错误");
                    }
                    throw new IOException("LLM 返回空响应");
                }
                state.setTotalTokensUsed(state.getTotalTokensUsed()
                        + response.getPromptTokens() + response.getCompletionTokens());
                state.setRetryCount(0);
                log.info("[AgentLoop] 流式API调用完成: promptTokens={}, completionTokens={}, hasToolCall={}",
                        response.getPromptTokens(), response.getCompletionTokens(), response.hasToolCall());
                return response;

            } catch (Exception e) {
                log.warn("[AgentLoop] Stream API call attempt {}/{} failed: {}", attempt + 1, MAX_RETRIES, e.getMessage());
                listener.onError("AI 服务调用失败，正在重试...");
                if (attempt < MAX_RETRIES - 1) {
                    sleepWithBackoff(attempt);
                }
            }
        }

        log.error("[AgentLoop] Stream API call exhausted all {} retries", MAX_RETRIES);
        return null;
    }

    // ─── Stage 4: Tool Execution ───

    private ToolExecutionResult executeTools(LLMResponse response, QueryParams params, AgentState state) {
        List<ToolDefinition> tools = toolRegistry.getAllTools();
        List<ToolResult> results = new ArrayList<>();
        List<Message> toolMessages = new ArrayList<>();
        List<ToolCall> calls = response.getToolCalls() != null
                ? response.getToolCalls()
                : (response.getToolCall() != null ? List.of(response.getToolCall()) : Collections.emptyList());

        for (ToolCall toolCall : calls) {
            ToolDefinition tool = findTool(tools, toolCall.getToolName());
            if (tool == null) {
                Message errMsg = new Message("tool", "错误: 工具 " + toolCall.getToolName() + " 不存在");
                errMsg.setToolCallId(toolCall.getId());
                toolMessages.add(errMsg);
                continue;
            }

            // Deny-First 权限检查
            PermissionManager.PermissionVerdict verdict = permissionManager.evaluate(
                    toolCall.getToolName(), toolCall.getParams());
            if (!verdict.isAllowed()) {
                Message denyMsg = new Message("tool", "操作被权限系统拒绝: " + verdict.getReason());
                denyMsg.setToolCallId(toolCall.getId());
                toolMessages.add(denyMsg);
                continue;
            }

            if (verdict.isRequiresApproval()) {
                ApprovalRequest approvalReq = approvalService.createRequest(
                        toolCall, verdict.getReason(), params.userId(), toolCall.getParams());
                return ToolExecutionResult.waitingApproval(approvalReq);
            }

            // 执行工具
            ToolResult result = tool.getExecutor().apply(toolCall.getParams());
            result.setToolCallId(toolCall.getId());
            results.add(result);

            Message resultMsg = new Message("tool", result.toJson());
            resultMsg.setToolCallId(toolCall.getId());
            toolMessages.add(resultMsg);

            // 从 ToolResult.data 中提取调整前快照（仅修改操作）
            Map<String, Object> beforeSnapshot = null;
            if (tool.isModification() && result.getData() != null && result.getData().containsKey("before")) {
                Object before = result.getData().get("before");
                if (before instanceof Map) {
                    beforeSnapshot = (Map<String, Object>) before;
                }
            }

            auditStore.record(AuditStore.AuditRecord.builder()
                    .sessionId(params.sessionId())
                    .toolName(toolCall.getToolName())
                    .params(toolCall.getParams())
                    .result(result)
                    .beforeSnapshot(beforeSnapshot)
                    .timestamp(System.currentTimeMillis())
                    .build());
        }

        return ToolExecutionResult.completed(results, toolMessages);
    }

    // ─── Stage 4: Tool Execution（流式） ───

    private ToolExecutionResult executeToolsStream(LLMResponse response, QueryParams params,
                                                    AgentState state, AgentEventListener listener) {
        List<ToolDefinition> tools = toolRegistry.getAllTools();
        List<ToolResult> results = new ArrayList<>();
        List<Message> toolMessages = new ArrayList<>();
        List<ToolCall> calls = response.getToolCalls() != null
                ? response.getToolCalls()
                : (response.getToolCall() != null ? List.of(response.getToolCall()) : Collections.emptyList());

        for (ToolCall toolCall : calls) {
            ToolDefinition tool = findTool(tools, toolCall.getToolName());
            if (tool == null) {
                Message errMsg = new Message("tool", "错误: 工具 " + toolCall.getToolName() + " 不存在");
                errMsg.setToolCallId(toolCall.getId());
                toolMessages.add(errMsg);
                continue;
            }

            listener.onToolCallDetected(toolCall.getToolName(), toolCall.getParams());

            // Deny-First 权限检查
            PermissionManager.PermissionVerdict verdict = permissionManager.evaluate(
                    toolCall.getToolName(), toolCall.getParams());
            if (!verdict.isAllowed()) {
                Message denyMsg = new Message("tool", "操作被权限系统拒绝: " + verdict.getReason());
                denyMsg.setToolCallId(toolCall.getId());
                toolMessages.add(denyMsg);
                continue;
            }

            if (verdict.isRequiresApproval()) {
                ApprovalRequest approvalReq = approvalService.createRequest(
                        toolCall, verdict.getReason(), params.userId(), toolCall.getParams());
                listener.onApprovalRequired(approvalReq);

                // 流式场景：通过 CompletableFuture 阻塞等待用户审批
                CompletableFuture<ApprovalDecision> approvalFuture =
                        approvalFutureManager.register(approvalReq.getRequestId());
                try {
                    ApprovalDecision decision = approvalFuture.get(5, TimeUnit.MINUTES);
                    if (decision.isRejected()) {
                        Message rejectMsg = new Message("tool", "操作被用户拒绝: " + decision.getReason());
                        rejectMsg.setToolCallId(toolCall.getId());
                        toolMessages.add(rejectMsg);
                        continue;
                    }
                    // 使用审批后的参数
                    Map<String, Object> execParams = decision.getModifiedParams() != null
                            ? decision.getModifiedParams() : toolCall.getParams();
                    toolCall.setParams(execParams);
                } catch (Exception e) {
                    approvalFutureManager.remove(approvalReq.getRequestId());
                    Message timeoutMsg = new Message("tool", "审批等待超时或取消");
                    timeoutMsg.setToolCallId(toolCall.getId());
                    toolMessages.add(timeoutMsg);
                    continue;
                }
            }

            // 执行工具
            listener.onToolExecutionStart(toolCall.getToolName());
            ToolResult result = tool.getExecutor().apply(toolCall.getParams());
            result.setToolCallId(toolCall.getId());
            results.add(result);

            Message resultMsg = new Message("tool", result.toJson());
            resultMsg.setToolCallId(toolCall.getId());
            toolMessages.add(resultMsg);

            listener.onToolExecutionResult(toolCall.getToolName(), result);

            auditStore.record(AuditStore.AuditRecord.builder()
                    .sessionId(params.sessionId())
                    .toolName(toolCall.getToolName())
                    .params(toolCall.getParams())
                    .result(result)
                    .timestamp(System.currentTimeMillis())
                    .build());
        }

        return ToolExecutionResult.completed(results, toolMessages);
    }

    // ─── Stage 5: Post-Turn ───

    private void postProcess(AgentState state, QueryParams params,
                              LLMResponse response, ToolExecutionResult toolResult) {
        // 衰减检测
        if (response != null && response.getContent() != null) {
            String prevContent = state.getLastAssistantContent();
            if (prevContent != null && prevContent.equals(response.getContent())) {
                state.setDiminishingCount(state.getDiminishingCount() + 1);
            } else {
                state.setDiminishingCount(0);
            }
            state.setLastAssistantContent(response.getContent());
        }

        // 记忆更新
        if (toolResult != null && !toolResult.getResults().isEmpty()) {
            for (ToolResult tr : toolResult.getResults()) {
                if (tr.getSummary() != null && (tr.getSummary().contains("调整") || tr.getSummary().contains("调优"))) {
                    memoryManager.recordAdjustment(
                            params.sessionContext() != null ? params.sessionContext().getAvailableServices() : "unknown",
                            null, tr.getSummary(), tr.toJson());
                }
            }
        }
    }

    // ─── Stage 6: Loop Control ───

    private boolean shouldStop(AgentState state) {
        if (state.getTurnCount() >= MAX_TURNS) {
            log.info("[AgentLoop] reached max turns {}", MAX_TURNS);
            return true;
        }
        if (state.getDiminishingCount() >= DIMINISHING_THRESHOLD) {
            log.warn("[AgentLoop] diminishing returns detected, stopping");
            return true;
        }
        if (state.getTotalTokensUsed() > 200000) {
            log.warn("[AgentLoop] total tokens {} exceeded budget", state.getTotalTokensUsed());
            return true;
        }
        return false;
    }

    // ─── 错误处理 ───

    private boolean handleError(AgentState state, Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;

        if (msg.contains("context_length_exceeded") || msg.contains("prompt_too_long")
                || msg.contains("too many tokens")) {
            if (contextManager != null) {
                state.setMessages(contextManager.prepareMessages(state.getMessages()));
                state.setRetryCount(state.getRetryCount() + 1);
                return state.getRetryCount() < 3;
            }
        }

        if (msg.contains("rate_limit") || msg.contains("429")) {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            return state.getRetryCount() < 3;
        }

        if (msg.contains("server_error") || msg.contains("503") || msg.contains("502")) {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            return state.getRetryCount() < 2;
        }

        return false;
    }

    // ─── 辅助方法 ───

    private String buildSystemPrompt(QueryParams params,
                                      List<ToolDefinition> tools, String knowledge, String memory) {
        SystemPromptBuilder.PromptContext ctx = new SystemPromptBuilder.PromptContext();
        ctx.setTools(tools);
        ctx.setEnvironmentInfo(buildEnvironmentInfo(params));
        if (knowledge != null) {
            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setTitle("参考知识");
            doc.setContent(knowledge);
            doc.setType("knowledge");
            ctx.setRelevantKnowledge(List.of(doc));
        }
        ctx.setMemoryContext(memory);
        return promptBuilder.build(ctx);
    }

    private String retrieveKnowledge(QueryParams params) {
        if (knowledgeService == null || params.userMessage() == null) return null;
        try {
            List<String> keywords = knowledgeService.extractKeywords(params.userMessage());
            if (keywords.isEmpty()) return null;
            KnowledgeRetriever.SearchContext ctx = new KnowledgeRetriever.SearchContext();
            ctx.setUserMessage(params.userMessage());
            if (params.sessionContext() != null) {
                ctx.setThreadPoolId(params.sessionContext().getAvailableServices());
            }
            List<KnowledgeDocument> docs = knowledgeService.search(keywords, ctx, 3);
            if (docs.isEmpty()) return null;

            StringBuilder sb = new StringBuilder();
            for (KnowledgeDocument doc : docs) {
                sb.append("### ").append(doc.getTitle()).append("\n");
                String content = doc.getContent();
                int bodyStart = content.indexOf("---", content.indexOf("---") + 3);
                if (bodyStart > 0) content = content.substring(bodyStart + 3).trim();
                if (content.length() > 500) content = content.substring(0, 500) + "...\n";
                sb.append(content).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[AgentLoop] knowledge retrieval failed", e);
            return null;
        }
    }

    private String retrieveMemory(QueryParams params) {
        if (memoryManager == null || params.userMessage() == null) return null;
        try {
            List<String> keywords = knowledgeService != null
                    ? knowledgeService.extractKeywords(params.userMessage())
                    : Collections.emptyList();
            String threadPoolId = params.sessionContext() != null
                    ? params.sessionContext().getAvailableServices() : null;
            return memoryManager.searchMemoryText(keywords, threadPoolId, null);
        } catch (Exception e) {
            log.warn("[AgentLoop] memory retrieval failed", e);
            return null;
        }
    }

    private String buildEnvironmentInfo(QueryParams params) {
        if (params.sessionContext() == null) return "";
        return "- 用户: " + params.sessionContext().getUserName() + "\n"
                + "- 可用服务: " + params.sessionContext().getAvailableServices() + "\n"
                + "- 来源: " + (params.sessionContext().getSource() != null
                ? params.sessionContext().getSource() : "AI_CHAT");
    }

    private ToolDefinition findTool(List<ToolDefinition> tools, String name) {
        return tools.stream().filter(t -> t.getName().equals(name)).findFirst().orElse(null);
    }

    private String buildSilentSystemPrompt(SilentRequest request) {
        SystemPromptBuilder.SilentPromptContext ctx = new SystemPromptBuilder.SilentPromptContext();
        ctx.setThreadPoolId(request.getThreadPoolId());
        ctx.setInstanceId(request.getInstanceId());
        ctx.setBusinessDescription(request.getBusinessDescription());

        // 配置文本：优先使用远程模式预渲染文本，回退到本地结构化对象
        if (request.getConfigText() != null && !request.getConfigText().isBlank()) {
            ctx.setConfigText(request.getConfigText());
        } else if (request.getConfig() != null) {
            ctx.setConfigText("- 核心线程数: " + request.getConfig().getCorePoolSize() + "\n"
                    + "- 最大线程数: " + request.getConfig().getMaximumPoolSize() + "\n"
                    + "- 队列容量: " + request.getConfig().getQueueCapacity() + "\n"
                    + "- 拒绝策略: " + request.getConfig().getRejectedHandler() + "\n"
                    + "- 存活时间: " + request.getConfig().getKeepAliveTime() + " 秒\n");
        }

        // 日志摘要：优先使用远程模式预渲染文本，回退到本地结构化对象
        if (request.getLogSummaryText() != null && !request.getLogSummaryText().isBlank()) {
            ctx.setLogSummary(request.getLogSummaryText());
        } else if (request.getRecentLogs() != null && !request.getRecentLogs().isEmpty()) {
            ctx.setLogSummary(buildLogSummary(request.getRecentLogs()));
        }

        return promptBuilder.buildSilentPrompt(ctx);
    }

    private String buildLogSummary(List<ThreadPoolLogRecord> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(logs.size()).append(" 条记录\n");
        int step = Math.max(1, logs.size() / 20);
        for (int i = 0; i < logs.size(); i += step) {
            ThreadPoolLogRecord r = logs.get(i);
            if (r.getQueueCapacity() > 0) {
                int usage = r.getQueueSize() * 100 / r.getQueueCapacity();
                sb.append("| ").append(formatTime(r.getTimestamp()))
                        .append(" | ").append(r.getActiveCount())
                        .append(" | ").append(r.getPoolSize())
                        .append(" | ").append(r.getQueueSize())
                        .append(" | ").append(r.getQueueCapacity())
                        .append(" | ").append(usage).append("%")
                        .append(" | ").append(r.getRejectCount())
                        .append(" |\n");
            }
        }
        return sb.toString();
    }

    private String buildSilentUserPrompt(SilentRequest request) {
        String target = request.getInstanceId() != null && !request.getInstanceId().isBlank()
                ? request.getThreadPoolId() + " 实例 " + request.getInstanceId()
                : request.getThreadPoolId();
        return "请分析线程池 " + target + " 的当前运行状况"
                + (request.getBusinessDescription() != null ? "（业务场景：" + request.getBusinessDescription() + "）" : "")
                + "，给出分析报告。";
    }

    private AgentReport parseAnalysisReport(LLMResponse response) {
        String content = response.getContent();
        String json = content;
        if (content.contains("```json")) {
            json = content.substring(content.indexOf("```json") + 7);
            if (json.contains("```")) json = json.substring(0, json.indexOf("```"));
        } else if (content.contains("```")) {
            json = content.substring(content.indexOf("```") + 3);
            if (json.contains("```")) json = json.substring(0, json.indexOf("```"));
        }
        json = json.trim();
        try {
            return JSON.parseObject(json, AgentReport.class);
        } catch (Exception e) {
            AgentReport report = new AgentReport();
            report.setSummary("分析完成");
            report.setAnalysis(content);
            report.setHealthStatus("UNKNOWN");
            return report;
        }
    }

    private String formatTime(long epochMillis) {
        java.time.LocalDateTime dt = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMillis), java.time.ZoneId.systemDefault());
        return dt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private void sleepWithBackoff(int attempt) {
        long delay = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attempt);
        try { Thread.sleep(delay); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ─── 内部数据结构 ───

    public record QueryParams(
            String sessionId,
            String userId,
            String userMessage,
            SessionContext sessionContext
    ) {}

    public static class AgentState {
        private List<Message> messages;
        private int turnCount;
        private int totalTokensUsed;
        private String lastToolResult;
        private String lastAssistantContent;
        private int diminishingCount;
        private int retryCount;

        public List<Message> getMessages() { return messages; }
        public void setMessages(List<Message> messages) { this.messages = messages; }
        public int getTurnCount() { return turnCount; }
        public void setTurnCount(int turnCount) { this.turnCount = turnCount; }
        public int getTotalTokensUsed() { return totalTokensUsed; }
        public void setTotalTokensUsed(int totalTokensUsed) { this.totalTokensUsed = totalTokensUsed; }
        public String getLastToolResult() { return lastToolResult; }
        public void setLastToolResult(String lastToolResult) { this.lastToolResult = lastToolResult; }
        public String getLastAssistantContent() { return lastAssistantContent; }
        public void setLastAssistantContent(String lastAssistantContent) { this.lastAssistantContent = lastAssistantContent; }
        public int getDiminishingCount() { return diminishingCount; }
        public void setDiminishingCount(int diminishingCount) { this.diminishingCount = diminishingCount; }
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    }

    @Data
    public static class AgentRequest {
        private String sessionId;
        private String userId;
        private String userMessage;
        private List<Message> history;
        private SessionContext sessionContext;
    }

    @Data
    public static class SessionContext {
        private String userName;
        private String availableServices;
        private String source;
    }

    @Data
    public static class Message {
        private String role;
        private String content;
        private String toolCallId;
        private List<ToolCall> toolCalls;
        public Message() {}
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    public static class ToolCall {
        private String id;
        private String toolName;
        private Map<String, Object> params;
    }

    @Data
    public static class AgentResponse {
        private String type;
        private String content;
        private String thinking;
        private ApprovalRequest approvalRequest;

        public static AgentResponse finalAnswer(String content, String thinking) {
            AgentResponse r = new AgentResponse();
            r.type = "final";
            r.content = content;
            r.thinking = thinking;
            return r;
        }

        public static AgentResponse waitingApproval(String thinking, ApprovalRequest req) {
            AgentResponse r = new AgentResponse();
            r.type = "approval";
            r.thinking = thinking;
            r.approvalRequest = req;
            return r;
        }
    }

    private static class ToolExecutionResult {
        private final List<ToolResult> results;
        private final List<Message> toolMessages;
        private final ApprovalRequest approvalRequest;
        private final boolean waitingApproval;

        private ToolExecutionResult(List<ToolResult> results, List<Message> toolMessages, ApprovalRequest approvalRequest, boolean waitingApproval) {
            this.results = results;
            this.toolMessages = toolMessages;
            this.approvalRequest = approvalRequest;
            this.waitingApproval = waitingApproval;
        }

        static ToolExecutionResult completed(List<ToolResult> results, List<Message> toolMessages) {
            return new ToolExecutionResult(results, toolMessages, null, false);
        }

        static ToolExecutionResult waitingApproval(ApprovalRequest req) {
            return new ToolExecutionResult(null, null, req, true);
        }

        List<ToolResult> getResults() { return results; }
        List<Message> getToolMessages() { return toolMessages; }
        ApprovalRequest getApprovalRequest() { return approvalRequest; }
        boolean isWaitingApproval() { return waitingApproval; }
    }
}

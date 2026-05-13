package com.baomihuahua.anticipa.dashboard.dev.server.controller;

import com.baomihuahua.anticipa.agent.AgentLoop;
import com.baomihuahua.anticipa.agent.ToolResult;
import com.baomihuahua.anticipa.agent.approval.ApprovalDecision;
import com.baomihuahua.anticipa.agent.approval.ApprovalRequest;
import com.baomihuahua.anticipa.agent.approval.ApprovalService;
import com.baomihuahua.anticipa.agent.event.AgentEventListener;
import com.baomihuahua.anticipa.agent.event.ApprovalFutureManager;
import com.baomihuahua.anticipa.agent.event.PipelineEvent;
import com.baomihuahua.anticipa.agent.memory.MemoryManager;
import com.baomihuahua.anticipa.agent.session.SessionManager;
import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * AI 对话 REST 接口。
 * <p>
 * 提供与 AI 助手对话的所有 REST 端点，包括流式 SSE 支持。
 * 从 anticipa-dashboard-dev-starter 迁移至控制台模块，
 * 使控制台不再依赖 starter，与 onethread 参考架构一致。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/anticipa-dashboard/ai")
public class AIChatController {

    private final AgentLoop agentLoop;
    private final SessionManager sessionManager;
    private final ApprovalService approvalService;
    private final ApprovalFutureManager approvalFutureManager;
    private final MemoryManager memoryManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AIChatController(AgentLoop agentLoop,
                             SessionManager sessionManager,
                             ApprovalService approvalService,
                             ApprovalFutureManager approvalFutureManager,
                             MemoryManager memoryManager) {
        this.agentLoop = agentLoop;
        this.sessionManager = sessionManager;
        this.approvalService = approvalService;
        this.approvalFutureManager = approvalFutureManager;
        this.memoryManager = memoryManager;
    }

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody ChatRequest chatReq) {
        SessionManager.Session session = getOrCreateSession(chatReq);

        AgentLoop.AgentRequest request = buildAgentRequest(chatReq, session);

        AgentLoop.AgentResponse response = agentLoop.process(request);

        session.getMessages().add(new SessionManager.Message("user", chatReq.getMessage(), System.currentTimeMillis()));
        if (response.getContent() != null) {
            session.getMessages().add(new SessionManager.Message("assistant", response.getContent(), System.currentTimeMillis()));
        }
        sessionManager.saveToDisk();

        Map<String, Object> result = new HashMap<>();
        result.put("reply", response.getContent());
        result.put("thinking", response.getThinking());
        result.put("sessionId", session.getSessionId());
        result.put("type", response.getType());

        if ("approval".equals(response.getType()) && response.getApprovalRequest() != null) {
            result.put("approvalRequest", response.getApprovalRequest());
        }

        return Result.success(result);
    }

    /**
     * 流式聊天 — 完整的 AgentLoop 6 阶段流水线 + SSE 推送。
     * <p>
     * 使用 POST 以支持长消息和结构化上下文参数。
     * 前端需使用 fetch + ReadableStream 消费 SSE（EventSource 不支持 POST）。
     * </p>
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest chatReq) {
        log.info("[AI-Chat] 收到流式请求: userId={}, sessionId={}, message={}",
                chatReq.getUserId(), chatReq.getSessionId(),
                chatReq.getMessage() != null ? chatReq.getMessage().substring(0, Math.min(80, chatReq.getMessage().length())) : "null");

        SessionManager.Session session = getOrCreateSession(chatReq);
        AgentLoop.AgentRequest request = buildAgentRequest(chatReq, session);

        log.info("[AI-Chat] 会话已创建/获取: sessionId={}", session.getSessionId());

        SseEmitter emitter = new SseEmitter(600_000L);

        executor.execute(() -> {
            AgentEventListener listener = new AgentEventListener() {
                @Override
                public void onKnowledgeRetrieved(String summary) {
                    log.info("[AI-Chat][SSE] 知识检索完成: summary={}", summary);
                }

                @Override
                public void onThinkingDelta(String delta) {
                    log.debug("[AI-Chat][SSE] 思考增量: length={}", delta != null ? delta.length() : 0);
                    sendSse(emitter, PipelineEvent.THINKING, Map.of("content", delta));
                }

                @Override
                public void onAnswerDelta(String delta) {
                    log.debug("[AI-Chat][SSE] 回答增量: length={}", delta != null ? delta.length() : 0);
                    sendSse(emitter, PipelineEvent.ANSWER, Map.of("text", delta));
                }

                @Override
                public void onToolCallDetected(String toolName, Map<String, Object> params) {
                    log.info("[AI-Chat][SSE] 工具调用: toolName={}, params={}", toolName, params);
                    sendSse(emitter, PipelineEvent.TOOL_CALL, Map.of("toolName", toolName, "params", params));
                }

                @Override
                public void onToolExecutionStart(String toolName) {
                    log.info("[AI-Chat][SSE] 工具执行开始: toolName={}", toolName);
                    sendSse(emitter, PipelineEvent.PROGRESS, Map.of("action", "executing_tool", "toolName", toolName));
                }

                @Override
                public void onToolExecutionResult(String toolName, ToolResult result) {
                    log.info("[AI-Chat][SSE] 工具执行完成: toolName={}, success={}, summary={}",
                            toolName, result.isSuccess(), result.getSummary());
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("toolName", toolName);
                    payload.put("success", result.isSuccess());
                    payload.put("summary", result.getSummary());
                    if (result.getData() != null) {
                        payload.put("data", result.getData());
                    }
                    sendSse(emitter, PipelineEvent.TOOL_RESULT, payload);
                }

                @Override
                public void onApprovalRequired(ApprovalRequest approvalReq) {
                    log.info("[AI-Chat][SSE] 审批请求: requestId={}, toolName={}",
                            approvalReq.getRequestId(), approvalReq.getToolName());
                    sendSse(emitter, PipelineEvent.APPROVAL_REQUIRED, approvalReq);
                }

                @Override
                public void onTurnProgress(int turn, int maxTurns, int tokens) {
                    log.info("[AI-Chat][SSE] 轮次进度: turn={}/{}, tokens={}", turn, maxTurns, tokens);
                    sendSse(emitter, PipelineEvent.PROGRESS,
                            Map.of("turn", turn, "maxTurns", maxTurns, "tokens", tokens));
                }

                @Override
                public void onComplete(String content, String thinking) {
                    log.info("[AI-Chat][SSE] 流式完成: contentLength={}, thinkingLength={}, sessionId={}",
                            content != null ? content.length() : 0,
                            thinking != null ? thinking.length() : 0,
                            session.getSessionId());
                    Map<String, Object> donePayload = new HashMap<>();
                    donePayload.put("content", content != null ? content : "");
                    donePayload.put("thinking", thinking != null ? thinking : "");
                    donePayload.put("sessionId", session.getSessionId());
                    sendSse(emitter, PipelineEvent.DONE, donePayload);
                    // 保存到会话
                    session.getMessages().add(new SessionManager.Message("user", chatReq.getMessage(), System.currentTimeMillis()));
                    if (content != null) {
                        session.getMessages().add(new SessionManager.Message("assistant", content, System.currentTimeMillis()));
                    }
                    sessionManager.saveToDisk();
                    emitter.complete();
                }

                @Override
                public void onError(String error) {
                    log.error("[AI-Chat][SSE] 流式错误: {}", error);
                    sendSse(emitter, PipelineEvent.ERROR, Map.of("message", error));
                }
            };

            try {
                log.info("[AI-Chat] 开始执行 AgentLoop 流式流水线: sessionId={}", session.getSessionId());
                agentLoop.processStream(request, listener);
                log.info("[AI-Chat] AgentLoop 流式流水线执行结束: sessionId={}", session.getSessionId());
            } catch (Exception e) {
                log.error("[AI-Chat] AgentLoop 流式流水线异常: sessionId={}", session.getSessionId(), e);
                listener.onError(e.getMessage());
            }
        });

        return emitter;
    }

    @PostMapping("/approval")
    public Result<Map<String, Object>> processApproval(@RequestBody ApprovalDecision decision) {
        ApprovalDecision processed = approvalService.processDecision(decision);
        ApprovalRequest req = ApprovalRequest.builder()
                .requestId(processed.getRequestId())
                .build();

        AgentLoop.AgentResponse response = agentLoop.continueAfterApproval(req, processed);

        Map<String, Object> result = new HashMap<>();
        result.put("reply", response.getContent());
        result.put("type", response.getType());
        return Result.success(result);
    }

    /**
     * 流式审批 — 接收用户对审批请求的决策，唤醒阻塞的 SSE 流。
     */
    @PostMapping("/approval/stream")
    public Result<Void> processApprovalStream(@RequestBody ApprovalDecision decision) {
        ApprovalDecision processed = approvalService.processDecision(decision);
        boolean resolved = approvalFutureManager.resolve(decision.getRequestId(), processed);
        if (!resolved) {
            log.warn("[APPROVAL] no pending stream found for requestId={}", decision.getRequestId());
            return Result.failure(404, "未找到对应的流式审批请求");
        }
        return Result.success();
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = new HashMap<>();
        status.put("available", true);
        status.put("model", "gpt-4");
        status.put("sessions", sessionManager.getUserSessions("anonymous").size());
        return Result.success(status);
    }

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getHistory(
            @RequestParam(required = false) String sessionId) {
        if (sessionId == null) {
            return Result.success(Collections.emptyList());
        }
        SessionManager.Session session = sessionManager.getSession(sessionId);
        if (session == null) {
            return Result.success(Collections.emptyList());
        }

        List<Map<String, Object>> history = session.getMessages().stream()
                .map(m -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("role", m.getRole());
                    item.put("content", m.getContent());
                    item.put("timestamp", m.getTimestamp());
                    return item;
                })
                .collect(Collectors.toList());
        return Result.success(history);
    }

    @DeleteMapping("/history")
    public Result<Void> clearHistory(@RequestParam String sessionId) {
        sessionManager.deleteSession(sessionId);
        return Result.success();
    }

    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> getSessions(
            @RequestParam(required = false, defaultValue = "anonymous") String userId) {
        List<SessionManager.Session> sessions = sessionManager.getUserSessions(userId);

        List<Map<String, Object>> result = sessions.stream()
                .map(s -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("sessionId", s.getSessionId());
                    item.put("title", s.getTitle());
                    item.put("createTime", s.getCreateTime());
                    item.put("messageCount", s.getMessages().size());
                    return item;
                })
                .collect(Collectors.toList());
        return Result.success(result);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        sessionManager.deleteSession(sessionId);
        memoryManager.clearSession(sessionId);
        return Result.success();
    }

    @GetMapping("/suggestions")
    public Result<List<String>> getSuggestions() {
        return Result.success(List.of(
                "查看所有线程池状态",
                "producer 线程池运行状况",
                "调整 consumer 线程池参数",
                "为什么任务被拒绝了？",
                "创建定时任务监控 producer"
        ));
    }

    @GetMapping("/examples")
    public Result<List<Map<String, String>>> getExamples() {
        List<Map<String, String>> examples = new ArrayList<>();
        examples.add(Map.of("title", "查看线程池状态", "description", "查询所有线程池的运行指标"));
        examples.add(Map.of("title", "诊断队列满", "description", "分析线程池队列满的原因"));
        examples.add(Map.of("title", "调优建议", "description", "获取线程池参数优化建议"));
        examples.add(Map.of("title", "创建定时任务", "description", "创建一个定时分析任务"));
        return Result.success(examples);
    }

    // ─── 辅助方法 ───

    private AgentLoop.AgentRequest buildAgentRequest(ChatRequest chatReq, SessionManager.Session session) {
        AgentLoop.AgentRequest request = new AgentLoop.AgentRequest();
        request.setSessionId(session.getSessionId());
        request.setUserId(chatReq.getUserId() != null ? chatReq.getUserId() : "anonymous");
        request.setUserMessage(chatReq.getMessage());

        AgentLoop.SessionContext ctx = new AgentLoop.SessionContext();
        ctx.setUserName(chatReq.getUserId() != null ? chatReq.getUserId() : "anonymous");
        ctx.setAvailableServices(chatReq.getContext() != null
                ? String.join(", ", chatReq.getContext()) : "");
        ctx.setSource("AI_CHAT");
        request.setSessionContext(ctx);

        if (session.getMessages() != null && !session.getMessages().isEmpty()) {
            request.setHistory(session.getMessages().stream()
                    .map(m -> new AgentLoop.Message(m.getRole(), m.getContent()))
                    .collect(Collectors.toList()));
        }
        return request;
    }

    private SessionManager.Session getOrCreateSession(ChatRequest chatReq) {
        if (chatReq.getSessionId() != null) {
            SessionManager.Session session = sessionManager.getSession(chatReq.getSessionId());
            if (session != null) {
                return session;
            }
        }
        return sessionManager.createSession(
                chatReq.getUserId() != null ? chatReq.getUserId() : "anonymous",
                chatReq.getMessage() != null
                        ? chatReq.getMessage().substring(0, Math.min(50, chatReq.getMessage().length()))
                        : "新对话");
    }

    private void sendSse(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(JSON.toJSONString(data)));
        } catch (IOException e) {
            log.debug("[SSE] client disconnected, event={}", eventName);
        }
    }

    @Data
    public static class ChatRequest {
        private String message;
        private String sessionId;
        private String userId;
        private List<String> context;
    }
}

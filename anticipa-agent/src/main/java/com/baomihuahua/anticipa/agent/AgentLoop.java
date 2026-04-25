package com.baomihuahua.anticipa.agent;

import com.baomihuahua.anticipa.agent.llm.AIClient;
import com.baomihuahua.anticipa.agent.llm.dto.LLMRequest;
import com.baomihuahua.anticipa.agent.llm.dto.LLMResponse;
import com.baomihuahua.anticipa.agent.safety.SafetyGuard;
import com.baomihuahua.anticipa.agent.safety.SafetyVerdict;
import com.baomihuahua.anticipa.agent.approval.*;
import com.baomihuahua.anticipa.agent.audit.AuditStore;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentLoop {

    private final AIClient aiClient;
    private final ToolRegistry toolRegistry;
    private final SafetyGuard safetyGuard;
    private final ApprovalService approvalService;
    private final AuditStore auditStore;

    private static final int MAX_ITERATIONS = 15;

    public AgentResponse process(AgentRequest request) {
        String systemPrompt = buildSystemPrompt(request.getSessionContext());
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt));
        if (request.getHistory() != null) {
            messages.addAll(request.getHistory());
        }
        messages.add(new Message("user", request.getUserMessage()));

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            LLMResponse llmResponse = aiClient.chat(new LLMRequest(messages));

            if (llmResponse.hasToolCall()) {
                ToolCall toolCall = llmResponse.getToolCall();
                ToolDefinition tool = toolRegistry.getTool(toolCall.getToolName());

                if (tool == null) {
                    messages.add(new Message("tool", "错误: 工具 " + toolCall.getToolName() + " 不存在"));
                    continue;
                }

                ToolResult result;
                if (tool.isModification()) {
                    SafetyVerdict verdict = safetyGuard.evaluate(toolCall, request.getSessionContext());
                    if (verdict.isRequiresApproval()) {
                        ApprovalRequest approvalReq = approvalService.createRequest(toolCall, verdict, request.getUserId());
                        return AgentResponse.waitingApproval(llmResponse.getThinking(), approvalReq);
                    }
                    if (!verdict.isAllowed()) {
                        messages.add(new Message("tool", "操作被安全守卫拒绝: " + verdict.getReason()));
                        continue;
                    }
                    result = tool.getExecutor().apply(toolCall.getParams());
                } else {
                    result = tool.getExecutor().apply(toolCall.getParams());
                }

                auditStore.record(AuditStore.AuditRecord.builder()
                        .sessionId(request.getSessionId())
                        .toolName(toolCall.getToolName())
                        .params(toolCall.getParams())
                        .result(result)
                        .timestamp(System.currentTimeMillis())
                        .build());

                messages.add(new Message("tool_result", result.toJson()));

            } else {
                return AgentResponse.finalAnswer(llmResponse.getContent(), llmResponse.getThinking());
            }
        }

        return AgentResponse.finalAnswer("抱歉，此问题需要多步分析，请尝试更具体的描述。", null);
    }

    public AgentResponse continueAfterApproval(ApprovalRequest approvalReq, ApprovalDecision decision) {
        if (decision.isRejected()) {
            return AgentResponse.finalAnswer("操作已被取消。" + decision.getReason(), null);
        }
        ToolDefinition tool = toolRegistry.getTool(approvalReq.getToolName());
        Map<String, Object> params = decision.getModifiedParams() != null ? decision.getModifiedParams() : approvalReq.getParams();
        ToolResult result = tool.getExecutor().apply(params);
        return AgentResponse.finalAnswer(result.getSummary(), null);
    }

    private String buildSystemPrompt(SessionContext ctx) {
        return "你是一个专业的动态线程池运维助手。你有以下工具可用：\n\n"
                + toolRegistry.buildToolsPrompt()
                + "\n\n规则:\n1. 每次只能调用一个工具\n2. 不要擅自执行修改操作——调用修改工具后系统会自动评估风险并请求审批\n3. 查询工具可以自由调用，无需审批\n4. 当前会话上下文:\n"
                + "   - 用户: " + ctx.getUserName() + "\n"
                + "   - 可用服务: " + ctx.getAvailableServices();
    }

    // ─── 内部数据结构 ───

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
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @Data
    public static class ToolCall {
        private String toolName;
        private Map<String, Object> params;
    }

    @Data
    public static class AgentResponse {
        private String type; // "final" or "approval"
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
}

package com.baomihuahua.anticipa.agent.approval;

import com.baomihuahua.anticipa.agent.AgentLoop;
import com.baomihuahua.anticipa.agent.safety.SafetyVerdict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ApprovalService {

    private final Map<String, ApprovalRequest> pendingRequests = new ConcurrentHashMap<>();

    public ApprovalRequest createRequest(AgentLoop.ToolCall toolCall, SafetyVerdict verdict, String userId) {
        ApprovalRequest request = ApprovalRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .userId(userId)
                .toolName(toolCall.getToolName())
                .params(toolCall.getParams())
                .riskLevel(verdict.getRiskLevel().name())
                .warnings(verdict.getWarnings())
                .build();
        pendingRequests.put(request.getRequestId(), request);
        log.info("[APPROVAL] 创建审批请求: {} - {} {} 风险={}", request.getRequestId(), toolCall.getToolName(), toolCall.getParams(), verdict.getRiskLevel());
        return request;
    }

    public ApprovalDecision processDecision(ApprovalDecision decision) {
        pendingRequests.remove(decision.getRequestId());
        log.info("[APPROVAL] 审批结果: {} - {}", decision.getRequestId(), decision.getAction());
        return decision;
    }
}

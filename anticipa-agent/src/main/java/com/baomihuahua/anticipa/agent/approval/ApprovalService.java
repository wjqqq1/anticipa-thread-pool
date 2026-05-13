package com.baomihuahua.anticipa.agent.approval;

import com.baomihuahua.anticipa.agent.AgentLoop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ApprovalService {

    private final Map<String, ApprovalRequest> pendingRequests = new ConcurrentHashMap<>();

    /**
     * 创建审批请求（新 API）。
     */
    public ApprovalRequest createRequest(AgentLoop.ToolCall toolCall, String reason, String userId,
                                          Map<String, Object> params) {
        String toolName = toolCall.getToolName();
        // 从 params 中提取 serviceName（线程池 ID 作为服务标识）
        String serviceName = "";
        Map<String, Object> effectiveParams = params != null ? params : toolCall.getParams();
        if (effectiveParams != null) {
            Object poolId = effectiveParams.get("thread_pool_id");
            if (poolId != null) serviceName = poolId.toString();
        }

        // 根据工具类型构建标题
        String title = buildApprovalTitle(toolName, serviceName);

        ApprovalRequest request = ApprovalRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .userId(userId)
                .toolName(toolName)
                .params(effectiveParams)
                .riskLevel("MEDIUM")
                .warnings(reason != null ? List.of(reason) : List.of())
                .title(title)
                .serviceName(serviceName)
                .reasoning(reason != null ? reason : "")
                .build();
        pendingRequests.put(request.getRequestId(), request);
        log.info("[APPROVAL] 创建审批请求: {} - {} {} reason={}", request.getRequestId(),
                toolCall.getToolName(), effectiveParams, reason);
        return request;
    }

    private String buildApprovalTitle(String toolName, String serviceName) {
        String servicePart = serviceName.isEmpty() ? "" : " " + serviceName;
        if (toolName == null) return "操作审批";
        return switch (toolName) {
            case "adjust_thread_pool" -> "调整" + servicePart + " 线程池配置";
            case "update_thread_pool_config" -> "修改" + servicePart + " 配置文件";
            case "create_scheduled_task" -> "创建定时任务" + servicePart;
            case "delete_scheduled_task" -> "删除定时任务" + servicePart;
            default -> "执行" + toolName + servicePart;
        };
    }

    public ApprovalDecision processDecision(ApprovalDecision decision) {
        pendingRequests.remove(decision.getRequestId());
        log.info("[APPROVAL] 审批结果: {} - {}", decision.getRequestId(), decision.getAction());
        return decision;
    }
}

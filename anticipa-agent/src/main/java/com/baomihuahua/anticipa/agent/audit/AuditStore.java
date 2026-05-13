package com.baomihuahua.anticipa.agent.audit;

import com.baomihuahua.anticipa.agent.ToolResult;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AuditStore {

    private final List<AuditRecord> records = new CopyOnWriteArrayList<>();

    public void record(AuditRecord record) {
        records.add(record);
        log.info("[AUDIT] {} - {} params={}", record.getSessionId(), record.getToolName(), record.getParams());
    }

    public List<AuditRecord> getHistory(String sessionId, int limit) {
        return records.stream()
                .filter(r -> r.getSessionId().equals(sessionId))
                .skip(Math.max(0, records.size() - limit))
                .collect(Collectors.toList());
    }

    /**
     * 多维度查询审计记录。
     *
     * @param sessionId   会话 ID（可选）
     * @param toolName    工具名（可选，支持前缀匹配，如 "adjust_" 匹配 adjust_thread_pool）
     * @param threadPoolId 线程池 ID（可选，从 params.thread_pool_id 中匹配）
     * @param limit       返回条数上限
     * @return 匹配的审计记录列表
     */
    public List<AuditRecord> search(String sessionId, String toolName, String threadPoolId, int limit) {
        return records.stream()
                .filter(r -> sessionId == null || sessionId.isEmpty() || sessionId.equals(r.getSessionId()))
                .filter(r -> toolName == null || toolName.isEmpty()
                        || toolName.equals(r.getToolName())
                        || (toolName.endsWith("_") && r.getToolName() != null && r.getToolName().startsWith(toolName))
                        || (toolName.endsWith("*") && r.getToolName() != null && r.getToolName().startsWith(toolName.substring(0, toolName.length() - 1))))
                .filter(r -> threadPoolId == null || threadPoolId.isEmpty()
                        || (r.getParams() != null && threadPoolId.equals(String.valueOf(r.getParams().get("thread_pool_id")))))
                .skip(Math.max(0, records.size() - limit * 3)) // 粗略预过滤
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Data
    @Builder
    public static class AuditRecord {
        private String auditId;
        private String sessionId;
        private long timestamp;
        private String userId;
        private String toolName;
        private Map<String, Object> params;
        private String riskLevel;
        private boolean requiresApproval;
        private ToolResult result;
        private long durationMs;
        /** 调整前的参数快照（仅修改操作记录） */
        private Map<String, Object> beforeSnapshot;
    }
}

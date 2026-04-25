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
    }
}

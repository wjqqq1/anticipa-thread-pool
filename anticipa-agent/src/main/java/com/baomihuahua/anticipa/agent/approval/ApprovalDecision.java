package com.baomihuahua.anticipa.agent.approval;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ApprovalDecision {
    private String requestId;
    private String userId;
    private String action; // APPROVED / REJECTED / MODIFIED
    private Map<String, Object> modifiedParams;
    private String reason;
    private long timestamp;

    public boolean isRejected() {
        return "REJECTED".equals(action);
    }
}

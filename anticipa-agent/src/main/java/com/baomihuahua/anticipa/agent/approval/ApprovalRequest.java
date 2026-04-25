package com.baomihuahua.anticipa.agent.approval;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ApprovalRequest {
    private String requestId;
    private long timestamp;
    private String userId;
    private String toolName;
    private Map<String, Object> params;
    private String riskLevel;
    private List<String> warnings;
}

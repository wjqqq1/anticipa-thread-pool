package com.baomihuahua.anticipa.agent.safety;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SafetyVerdict {
    private boolean allowed;
    private boolean requiresApproval;
    private SafetyGuard.RiskLevel riskLevel;
    private List<String> warnings;
    private String reason;
}

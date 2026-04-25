package com.baomihuahua.anticipa.agent.safety;

import com.baomihuahua.anticipa.agent.AgentLoop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SafetyGuard {

    public SafetyVerdict evaluate(AgentLoop.ToolCall toolCall, AgentLoop.SessionContext context) {
        List<String> warnings = new ArrayList<>();
        RiskLevel riskLevel = RiskLevel.LOW;
        Map<String, Object> params = toolCall.getParams();

        // 规则: 调整幅度检查
        if (params.containsKey("corePoolSize")) {
            int newCore = toInt(params.get("corePoolSize"));
            int currentCore = toInt(params.getOrDefault("currentCorePoolSize", newCore));
            if (currentCore > 0) {
                double ratio = Math.abs(newCore - currentCore) / (double) currentCore;
                if (ratio > 0.5) {
                    warnings.add("核心线程调整幅度超过 50%");
                    riskLevel = upgrade(riskLevel, RiskLevel.HIGH);
                } else if (ratio > 0.3) {
                    warnings.add("核心线程调整幅度超过 30%");
                    riskLevel = upgrade(riskLevel, RiskLevel.MEDIUM);
                }
            }
        }

        // 来源 + 风险等级 → 是否需审批
        String source = context.getSource() != null ? context.getSource() : "AI_CHAT";
        boolean requiresApproval = riskLevel.ordinal() >= RiskLevel.MEDIUM.ordinal() || "AUTO_OPTIMIZER".equals(source);

        return SafetyVerdict.builder()
                .allowed(true)
                .requiresApproval(requiresApproval)
                .riskLevel(riskLevel)
                .warnings(warnings)
                .reason(requiresApproval ? "操作风险等级 " + riskLevel + "，需人工审批" : "低风险操作，自动放行")
                .build();
    }

    private int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) return Integer.parseInt((String) val);
        return 0;
    }

    private RiskLevel upgrade(RiskLevel current, RiskLevel target) {
        return target.ordinal() > current.ordinal() ? target : current;
    }

    public enum RiskLevel { LOW, MEDIUM, HIGH }
}

package com.baomihuahua.anticipa.agent.permission;

import java.util.Map;

/**
 * 风险评估器。
 * <p>
 * 评估工具调用的风险等级：LOW / MEDIUM / HIGH。
 * </p>
 */
public class RiskEvaluator {

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    /**
     * 评估工具调用的风险等级。
     *
     * @param toolName 工具名称
     * @param params   工具参数
     * @return 风险等级
     */
    public RiskLevel evaluate(String toolName, Map<String, Object> params) {
        // 只读工具：低风险
        if (toolName.startsWith("query_") || toolName.startsWith("get_")) {
            return RiskLevel.LOW;
        }

        // 批量操作：高风险
        if ("batch_adjust".equals(toolName)) {
            return RiskLevel.HIGH;
        }

        // Nacos 配置修改：高风险（持久化，影响所有实例）
        if ("update_thread_pool_config".equals(toolName)) {
            return RiskLevel.HIGH;
        }

        // AUTO_ADJUST 定时任务：高风险（会自动修改参数）
        if ("create_scheduled_task".equals(toolName)) {
            if (params != null && "AUTO_ADJUST".equals(String.valueOf(params.get("action")))) {
                return RiskLevel.HIGH;
            }
            return RiskLevel.LOW;
        }

        // 只读工具（含 list_/analyze_）：低风险
        if (toolName.startsWith("list_") || toolName.startsWith("analyze_")) {
            return RiskLevel.LOW;
        }

        // 检查调整幅度
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getValue() instanceof Number) {
                    double val = ((Number) entry.getValue()).doubleValue();
                    // 过大数值 → 高风险
                    if (val > 500) return RiskLevel.HIGH;
                    if (val > 200) return RiskLevel.MEDIUM;
                }
            }
        }

        // 修改操作：默认中等风险
        return RiskLevel.MEDIUM;
    }

    /**
     * 将数字风险等级映射为名称。
     */
    public static String levelName(RiskLevel level) {
        return switch (level) {
            case LOW -> "LOW";
            case MEDIUM -> "MEDIUM";
            case HIGH -> "HIGH";
        };
    }
}

package com.baomihuahua.anticipa.agent.permission;

/**
 * 权限规则模型（Deny-First）。
 * <p>
 * 权限判定优先级（从高到低）：
 * deny 规则 > ask 规则（需审批） > allow 规则
 * </p>
 */
public class PermissionRule {

    /** 规则作用的目标工具名（支持通配符 *，如 "query_*"） */
    private String toolPattern;

    /** 规则类型：deny / ask / allow */
    private String type;

    /** SpEL 或简单条件表达式（如 "params.corePoolSize == 0"） */
    private String condition;

    /** 规则说明/原因 */
    private String reason;

    public PermissionRule() {}

    public PermissionRule(String toolPattern, String type, String condition, String reason) {
        this.toolPattern = toolPattern;
        this.type = type;
        this.condition = condition;
        this.reason = reason;
    }

    public String getToolPattern() { return toolPattern; }
    public void setToolPattern(String toolPattern) { this.toolPattern = toolPattern; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isDeny() { return "deny".equalsIgnoreCase(type); }
    public boolean isAsk() { return "ask".equalsIgnoreCase(type); }
    public boolean isAllow() { return "allow".equalsIgnoreCase(type); }
}

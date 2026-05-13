package com.baomihuahua.anticipa.agent.permission;

import com.baomihuahua.anticipa.agent.ToolDefinition;
import com.baomihuahua.anticipa.agent.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 权限管理器（Deny-First 模型）。
 * <p>
 * 参考 Claude Code 的多层权限模型：
 * 权限判定优先级（从高到低）：deny 规则 > ask 规则（需审批） > allow 规则
 * </p>
 * <p>
 * 权限规则来源（按优先级合并）：
 * 1. 系统内置规则（硬编码）
 * 2. 应用配置规则（YAML 配置）
 * 3. 运行时动态规则（API 添加）
 * </p>
 */
public class PermissionManager {

    private static final Logger log = LoggerFactory.getLogger(PermissionManager.class);

    private final ToolRegistry toolRegistry;
    private final List<PermissionRule> rules = new CopyOnWriteArrayList<>();

    public PermissionManager(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        initBuiltinRules();
    }

    /**
     * 初始化系统内置规则。
     */
    private void initBuiltinRules() {
        rules.add(new PermissionRule("adjust_thread_pool", "deny",
                "params.corePoolSize == 0", "禁止将核心线程数设为 0"));
        rules.add(new PermissionRule("adjust_thread_pool", "deny",
                "params.maximumPoolSize == 0", "禁止将最大线程数设为 0"));
        rules.add(new PermissionRule("adjust_thread_pool", "deny",
                "params.maximumPoolSize > 500", "最大线程数不能超过 500"));
        rules.add(new PermissionRule("query_*", "allow",
                null, "所有查询操作自动放行"));
        rules.add(new PermissionRule("batch_adjust", "ask",
                null, "批量调整操作始终需要审批"));
        rules.add(new PermissionRule("create_scheduled_task", "ask",
                "params.action == AUTO_ADJUST", "AUTO_ADJUST 策略会自动修改线程池参数，需人工确认"));
        rules.add(new PermissionRule("update_thread_pool_config", "deny",
                "params.core_pool_size == 0", "禁止将核心线程数设为 0"));
        rules.add(new PermissionRule("update_thread_pool_config", "deny",
                "params.maximum_pool_size == 0", "禁止将最大线程数设为 0"));
        rules.add(new PermissionRule("update_thread_pool_config", "deny",
                "params.maximum_pool_size > 500", "最大线程数不能超过 500"));
        rules.add(new PermissionRule("update_thread_pool_config", "ask",
                null, "修改 Nacos 配置文件影响所有实例，始终需要审批"));
        log.info("[Permission] initialized {} built-in rules", rules.size());
    }

    /**
     * 评估工具调用权限。
     *
     * @param toolName 工具名称
     * @param params   工具参数
     * @return 权限判定结果
     */
    public PermissionVerdict evaluate(String toolName, Map<String, Object> params) {
        ToolDefinition tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            return PermissionVerdict.deny("工具不存在: " + toolName);
        }

        // 按规则优先级检查
        for (PermissionRule rule : rules) {
            if (!matchesTool(rule.getToolPattern(), toolName)) {
                continue;
            }

            if (rule.isDeny()) {
                if (matchesCondition(rule.getCondition(), params)) {
                    log.warn("[Permission] DENY: {} - {}", toolName, rule.getReason());
                    return PermissionVerdict.deny(rule.getReason());
                }
            }

            if (rule.isAsk()) {
                if (rule.getCondition() == null || matchesCondition(rule.getCondition(), params)) {
                    log.info("[Permission] ASK: {} - {}", toolName, rule.getReason());
                    return PermissionVerdict.ask(rule.getReason());
                }
            }
        }

        // 没有 deny/ask 命中 → allow（但修改操作默认需要审批）
        if (tool.isModification() || tool.isNeedsApproval()) {
            log.info("[Permission] DEFAULT_ASK: {} - 修改操作需审批", toolName);
            return PermissionVerdict.ask("修改操作需人工确认");
        }

        return PermissionVerdict.allow();
    }

    /**
     * 添加运行时规则。
     */
    public void addRule(PermissionRule rule) {
        rules.add(rule);
        log.info("[Permission] added rule: {} {} {}", rule.getType(), rule.getToolPattern(), rule.getReason());
    }

    /**
     * 删除运行时规则。
     */
    public void removeRule(String toolPattern, String type) {
        rules.removeIf(r -> r.getToolPattern().equals(toolPattern) && r.getType().equals(type));
    }

    /**
     * 获取所有规则。
     */
    public List<PermissionRule> getRules() {
        return List.copyOf(rules);
    }

    /**
     * 通过 YAML 配置加载规则。
     */
    public void loadConfigRules(List<PermissionRule> denyRules,
                                 List<PermissionRule> askRules,
                                 List<PermissionRule> allowRules) {
        if (denyRules != null) {
            denyRules.forEach(r -> { r.setType("deny"); rules.add(r); });
        }
        if (askRules != null) {
            askRules.forEach(r -> { r.setType("ask"); rules.add(r); });
        }
        if (allowRules != null) {
            allowRules.forEach(r -> { r.setType("allow"); rules.add(r); });
        }
        log.info("[Permission] loaded {} config rules", 
                (denyRules != null ? denyRules.size() : 0) +
                (askRules != null ? askRules.size() : 0) +
                (allowRules != null ? allowRules.size() : 0));
    }

    private boolean matchesTool(String pattern, String toolName) {
        if (pattern == null) return false;
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return toolName != null && toolName.startsWith(prefix);
        }
        return pattern.equals(toolName);
    }

    private boolean matchesCondition(String condition, Map<String, Object> params) {
        if (condition == null) return true; // 无条件规则默认匹配
        if (params == null) return false;

        // 简单条件解析：支持 "params.xxx == yyy" 或 "params.xxx > yyy" 格式
        try {
            String expr = condition.trim();
            if (expr.startsWith("params.")) {
                expr = expr.substring(7); // 去掉 "params."
            }

            String field;
            String operator;
            String expectedValue;

            if (expr.contains(" == ")) {
                String[] parts = expr.split(" == ");
                field = parts[0].trim();
                operator = "==";
                expectedValue = parts[1].trim();
            } else if (expr.contains(" > ")) {
                String[] parts = expr.split(" > ");
                field = parts[0].trim();
                operator = ">";
                expectedValue = parts[1].trim();
            } else if (expr.contains(" < ")) {
                String[] parts = expr.split(" < ");
                field = parts[0].trim();
                operator = "<";
                expectedValue = parts[1].trim();
            } else {
                return false;
            }

            Object actualValue = params.get(field);
            if (actualValue == null) return false;

            return switch (operator) {
                case "==" -> compareEqual(actualValue, expectedValue);
                case ">" -> compareNumeric(actualValue, expectedValue) > 0;
                case "<" -> compareNumeric(actualValue, expectedValue) < 0;
                default -> false;
            };
        } catch (Exception e) {
            log.warn("[Permission] condition parse error: {}", condition, e);
            return false;
        }
    }

    private boolean compareEqual(Object actual, String expected) {
        if (actual instanceof Number && expected.chars().allMatch(Character::isDigit)) {
            return ((Number) actual).intValue() == Integer.parseInt(expected);
        }
        return actual.toString().equals(expected);
    }

    private int compareNumeric(Object actual, String expected) {
        double a = actual instanceof Number ? ((Number) actual).doubleValue() : Double.parseDouble(actual.toString());
        double e = Double.parseDouble(expected);
        return Double.compare(a, e);
    }

    /**
     * 权限判定结果。
     */
    public static class PermissionVerdict {
        private final boolean allowed;
        private final boolean requiresApproval;
        private final String reason;

        private PermissionVerdict(boolean allowed, boolean requiresApproval, String reason) {
            this.allowed = allowed;
            this.requiresApproval = requiresApproval;
            this.reason = reason;
        }

        public static PermissionVerdict allow() {
            return new PermissionVerdict(true, false, null);
        }

        public static PermissionVerdict ask(String reason) {
            return new PermissionVerdict(true, true, reason);
        }

        public static PermissionVerdict deny(String reason) {
            return new PermissionVerdict(false, false, reason);
        }

        public boolean isAllowed() { return allowed; }
        public boolean isRequiresApproval() { return requiresApproval; }
        public String getReason() { return reason; }
    }
}

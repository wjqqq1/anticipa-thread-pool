package com.baomihuahua.anticipa.agent.tool;

/**
 * 工具分类枚举。
 * <p>
 * 参考 Anthropic MCP 实践指南：工具应接意图分组而非按 API 分。
 * 用于 ToolSearchService 按意图筛选工具，减少 System Prompt 中工具定义的 Token 消耗。
 * </p>
 */
public enum ToolCategory {
    /** 查询类：查状态、查指标、查配置 */
    QUERY,
    /** 调整类：调参数、批量调、回滚 */
    ADJUST,
    /** 诊断类：查历史、查告警、分析趋势 */
    DIAGNOSE,
    /** 系统类：实例信息、健康检查 */
    SYSTEM
}

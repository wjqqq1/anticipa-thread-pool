package com.baomihuahua.anticipa.agent;

import com.baomihuahua.anticipa.agent.tool.ToolCategory;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.function.Function;

@Data
@Builder
public class ToolDefinition {
    private String name;
    private String description;
    /** 工具分类，用于 ToolSearch 按意图筛选 */
    private ToolCategory category;
    private boolean modification;
    private boolean needsApproval;
    /** 是否可并发执行（只读工具 = true，可与其他只读工具同时调用） */
    private boolean concurrencySafe;
    private Map<String, Object> parameterSchema;
    private Function<Map<String, Object>, ToolResult> executor;
}

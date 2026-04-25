package com.baomihuahua.anticipa.agent;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.function.Function;

@Data
@Builder
public class ToolDefinition {
    private String name;
    private String description;
    private boolean modification;
    private boolean needsApproval;
    private Map<String, Object> parameterSchema;
    private Function<Map<String, Object>, ToolResult> executor;
}

package com.baomihuahua.anticipa.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter.Feature;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ToolRegistry {

    @Getter
    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();

    public void register(ToolDefinition tool) {
        tools.put(tool.getName(), tool);
    }

    public ToolDefinition getTool(String name) {
        return tools.get(name);
    }

    public List<ToolDefinition> getAllTools() {
        return List.copyOf(tools.values());
    }

    public String buildToolsPrompt() {
        StringBuilder sb = new StringBuilder("\n## 可用工具\n\n");
        for (ToolDefinition tool : tools.values()) {
            sb.append("### ").append(tool.getName()).append("\n");
            sb.append("描述: ").append(tool.getDescription()).append("\n");
            sb.append("是否需要审批: ").append(tool.isNeedsApproval() ? "是" : "否").append("\n");
            sb.append("参数: ```json\n").append(JSON.toJSONString(tool.getParameterSchema(), Feature.PrettyFormat)).append("\n```\n\n");
        }
        return sb.toString();
    }
}

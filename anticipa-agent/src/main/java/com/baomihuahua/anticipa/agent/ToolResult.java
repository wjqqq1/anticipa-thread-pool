package com.baomihuahua.anticipa.agent;

import com.alibaba.fastjson2.JSON;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ToolResult {
    private boolean success;
    private String summary;
    private Map<String, Object> data;
    private String rawJson;

    public static ToolResult success(String summary, Object data) {
        return ToolResult.builder()
                .success(true)
                .summary(summary)
                .data(data instanceof Map ? (Map<String, Object>) data : JSON.parseObject(JSON.toJSONString(data)))
                .build();
    }

    public String toJson() {
        if (rawJson != null) return rawJson;
        return JSON.toJSONString(this);
    }
}

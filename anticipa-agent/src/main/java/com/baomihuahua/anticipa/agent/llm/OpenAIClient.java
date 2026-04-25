package com.baomihuahua.anticipa.agent.llm;

import com.baomihuahua.anticipa.agent.AgentLoop;
import com.baomihuahua.anticipa.agent.llm.dto.LLMRequest;
import com.baomihuahua.anticipa.agent.llm.dto.LLMResponse;
import okhttp3.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class OpenAIClient implements AIClient {

    private final OkHttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public OpenAIClient(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        List<Map<String, String>> messages = request.getMessages().stream()
                .map(m -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("role", m.getRole());
                    map.put("content", m.getContent());
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.7);

        Request req = new Request.Builder()
                .url(baseUrl + "/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(JSON.toJSONString(body), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(req).execute()) {
            String respBody = response.body().string();
            JSONObject json = JSON.parseObject(respBody);
            String content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            // 检查是否有工具调用格式
            AgentLoop.ToolCall toolCall = parseToolCall(content);
            if (toolCall != null) {
                return LLMResponse.withToolCall(toolCall, content);
            }
            return LLMResponse.withContent(content, null);
        } catch (IOException e) {
            log.error("LLM 调用失败", e);
            return LLMResponse.withContent("抱歉，AI 服务暂时不可用，请稍后重试。", null);
        }
    }

    private AgentLoop.ToolCall parseToolCall(String content) {
        if (content == null) return null;
        // 解析格式: TOOL_CALL: {"name": "...", "params": {...}}
        if (content.contains("TOOL_CALL:")) {
            try {
                String jsonStr = content.substring(content.indexOf("TOOL_CALL:") + 10).trim();
                AgentLoop.ToolCall call = JSON.parseObject(jsonStr, AgentLoop.ToolCall.class);
                return call;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}

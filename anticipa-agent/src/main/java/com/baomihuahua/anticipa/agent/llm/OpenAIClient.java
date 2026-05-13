package com.baomihuahua.anticipa.agent.llm;

import com.baomihuahua.anticipa.agent.AgentLoop;
import com.baomihuahua.anticipa.agent.ToolDefinition;
import com.baomihuahua.anticipa.agent.llm.dto.LLMRequest;
import com.baomihuahua.anticipa.agent.llm.dto.LLMResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class OpenAIClient implements AIClient {

    private final OkHttpClient httpClient;
    private final OkHttpClient streamingHttpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public OpenAIClient(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "";
        this.model = model;
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);
        this.httpClient = builder.build();
        this.streamingHttpClient = builder.readTimeout(300, TimeUnit.SECONDS).build();
    }

    @Override
    public LLMResponse chat(LLMRequest request) {
        return doChat(request, null);
    }

    @Override
    public LLMResponse chatWithTools(LLMRequest request, List<ToolDefinition> tools) {
        return doChat(request, tools);
    }

    @Override
    public void chatStream(LLMRequest request, List<ToolDefinition> tools,
                           Consumer<StreamEvent> onEvent, Consumer<LLMResponse> onComplete) {
        List<Map<String, Object>> messages = convertMessages(request.getMessages());
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", request.getTemperature());
        body.put("stream", true);
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", convertToolsToOpenAI(tools));
        }

        String jsonBody = JSON.toJSONString(body);
        String url = baseUrl + "/v1/chat/completions";
        log.info("[LLM] stream request: url={}, model={}, tools={}, messages={}", url, model,
                tools != null ? tools.stream().map(ToolDefinition::getName).collect(Collectors.toList()) : "none",
                messages.size());

        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        streamingHttpClient.newCall(req).enqueue(new Callback() {
            private final StringBuilder contentBuffer = new StringBuilder();
            private final StringBuilder thinkingBuffer = new StringBuilder();
            // 累积流式 tool_calls：按 index 存储，每个元素包含 id、name 和 arguments
            private final Map<Integer, String> toolCallIds = new HashMap<>();
            private final Map<Integer, String> toolCallNames = new HashMap<>();
            private final Map<Integer, StringBuilder> toolCallArgs = new HashMap<>();

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                log.error("[LLM] stream request failed: url={}", url, e);
                onEvent.accept(new StreamEvent("error", "AI 服务暂时不可用: " + e.getMessage()));
                onComplete.accept(LLMResponse.withError("抱歉，AI 服务暂时不可用。", null));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                log.info("[LLM] stream response received: status={}", response.code());

                // 检查 HTTP 状态码，非流式 doChat 也有此检查
                if (!response.isSuccessful()) {
                    String errorBody = "";
                    try (ResponseBody respBody = response.body()) {
                        if (respBody != null) errorBody = respBody.string();
                    } catch (Exception ignored) {}
                    log.error("[LLM] stream API error: status={}, body={}", response.code(), errorBody);
                    LLMResponse errorResp = handleApiError(response.code(), errorBody);
                    onComplete.accept(errorResp);
                    return;
                }

                try (ResponseBody respBody = response.body()) {
                    if (respBody == null) {
                        log.warn("[LLM] stream response body is null");
                        onComplete.accept(LLMResponse.withContent("", null));
                        return;
                    }
                    BufferedReader reader = new BufferedReader(respBody.byteStream());
                    String line;
                    int chunkCount = 0;
                    while ((line = reader.readLine()) != null) {
                        // 兼容 "data: " 和 "data:" 两种 SSE 格式
                        String data = null;
                        if (line.startsWith("data: ")) {
                            data = line.substring(6).trim();
                        } else if (line.startsWith("data:")) {
                            data = line.substring(5).trim();
                        }

                        if (data != null) {
                            if ("[DONE]".equals(data)) break;
                            chunkCount++;
                            try {
                                processStreamChunk(data, onEvent);
                            } catch (Exception e) {
                                log.warn("[LLM] failed to parse stream chunk #{}: {}", chunkCount, data, e);
                            }
                        }
                    }

                    // 完成回调：根据是否累积了 tool_calls 构建不同的 LLMResponse
                    if (!toolCallNames.isEmpty()) {
                        // 有工具调用 — 构建带 toolCalls 的 LLMResponse
                        List<AgentLoop.ToolCall> calls = new ArrayList<>();
                        for (int i = 0; i < toolCallNames.size(); i++) {
                            String name = toolCallNames.get(i);
                            String args = toolCallArgs.getOrDefault(i, new StringBuilder()).toString().trim();
                            AgentLoop.ToolCall tc = new AgentLoop.ToolCall();
                            tc.setId(toolCallIds.get(i));
                            tc.setToolName(name != null ? name : "");
                            try {
                                tc.setParams(!args.isEmpty() ? JSON.parseObject(args) : new HashMap<>());
                            } catch (Exception e) {
                                log.warn("[LLM] failed to parse tool_call arguments: {}", args);
                                tc.setParams(new HashMap<>());
                            }
                            calls.add(tc);
                        }
                        log.info("[LLM] stream completed: chunks={}, tool_calls={}, contentLength={}",
                                chunkCount, calls.size(), contentBuffer.length());
                        onComplete.accept(LLMResponse.withToolCalls(calls));
                    } else {
                        // 纯文本回答
                        String content = contentBuffer.toString();
                        log.info("[LLM] stream completed: chunks={}, contentLength={}, thinkingLength={}",
                                chunkCount, content.length(), thinkingBuffer.length());
                        onComplete.accept(LLMResponse.withContent(content, thinkingBuffer.toString()));
                    }
                } catch (Exception e) {
                    log.error("[LLM] stream response error", e);
                    onComplete.accept(LLMResponse.withError("AI 流式响应异常: " + e.getMessage(), null));
                }
            }

            private void processStreamChunk(String data, Consumer<StreamEvent> onEvent) {
                JSONObject chunk = JSON.parseObject(data);
                JSONArray choices = chunk.getJSONArray("choices");
                if (choices == null || choices.isEmpty()) return;

                JSONObject choice = choices.getJSONObject(0);
                JSONObject delta = choice.getJSONObject("delta");
                if (delta == null) return;

                // 检查 reasoning_content（深度求索等模型的思维链）
                String reasoning = delta.getString("reasoning_content");
                if (reasoning != null && !reasoning.isEmpty()) {
                    thinkingBuffer.append(reasoning);
                    onEvent.accept(new StreamEvent("thinking", reasoning));
                }

                // 检查 tool_calls 增量 — 累积 id、name 和 arguments
                JSONArray toolCallsDelta = delta.getJSONArray("tool_calls");
                if (toolCallsDelta != null && !toolCallsDelta.isEmpty()) {
                    for (int i = 0; i < toolCallsDelta.size(); i++) {
                        JSONObject tcDelta = toolCallsDelta.getJSONObject(i);
                        int idx = tcDelta.getIntValue("index", 0);

                        // 提取 tool_call id（通常在首个 chunk 中）
                        String tcId = tcDelta.getString("id");
                        if (tcId != null && !tcId.isEmpty()) {
                            toolCallIds.put(idx, tcId);
                            log.debug("[LLM] stream tool_call id: index={}, id={}", idx, tcId);
                        }

                        // 首个 chunk 包含 function.name
                        JSONObject func = tcDelta.getJSONObject("function");
                        if (func != null) {
                            String funcName = func.getString("name");
                            if (funcName != null && !funcName.isEmpty()) {
                                toolCallNames.put(idx, funcName);
                                log.debug("[LLM] stream tool_call name: index={}, name={}", idx, funcName);
                            }
                            String argsDelta = func.getString("arguments");
                            if (argsDelta != null && !argsDelta.isEmpty()) {
                                toolCallArgs.computeIfAbsent(idx, k -> new StringBuilder()).append(argsDelta);
                            }
                        }
                    }
                    return;
                }

                // 普通内容
                String contentDelta = delta.getString("content");
                if (contentDelta != null && !contentDelta.isEmpty()) {
                    contentBuffer.append(contentDelta);
                    onEvent.accept(new StreamEvent("answer", contentDelta));
                }

                // 检查 finish_reason
                String finishReason = choice.getString("finish_reason");
                if ("tool_calls".equals(finishReason)) {
                    log.debug("[LLM] stream finish_reason=tool_calls, accumulated {} tool calls", toolCallNames.size());
                }
            }
        });
    }

    /**
     * 核心聊天方法。
     */
    private LLMResponse doChat(LLMRequest request, List<ToolDefinition> tools) {
        List<Map<String, Object>> messages = convertMessages(request.getMessages());
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", request.getTemperature());
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }

        // 原生 Function Calling：注入工具定义
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", convertToolsToOpenAI(tools));
        }

        String jsonBody = JSON.toJSONString(body);
        log.debug("[LLM] request: model={}, tools={}, messages={}", model,
                tools != null ? tools.stream().map(ToolDefinition::getName).collect(Collectors.toList()) : "none",
                messages.size());

        Request req = new Request.Builder()
                .url(baseUrl + "/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(req).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("[LLM] API error: status={}, body={}", response.code(), respBody);
                return handleApiError(response.code(), respBody);
            }

            JSONObject json = JSON.parseObject(respBody);
            JSONObject message = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message");

            // 解析原生 tool_calls
            JSONArray toolCalls = message.getJSONArray("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                List<AgentLoop.ToolCall> parsedCalls = new ArrayList<>();
                for (int i = 0; i < toolCalls.size(); i++) {
                    JSONObject tc = toolCalls.getJSONObject(i);
                    JSONObject func = tc.getJSONObject("function");
                    AgentLoop.ToolCall call = new AgentLoop.ToolCall();
                    call.setId(tc.getString("id"));
                    call.setToolName(func.getString("name"));
                    try {
                        String args = func.getString("arguments");
                        call.setParams(args != null ? JSON.parseObject(args) : new HashMap<>());
                    } catch (Exception e) {
                        call.setParams(new HashMap<>());
                    }
                    parsedCalls.add(call);
                }
                log.debug("[LLM] native tool_calls: {}", parsedCalls.stream()
                        .map(AgentLoop.ToolCall::getToolName).collect(Collectors.toList()));
                return LLMResponse.withToolCalls(parsedCalls);
            }

            // 回退：检查文本 TOOL_CALL: 格式（兼容旧工具）
            String content = message.getString("content");
            AgentLoop.ToolCall textCall = parseTextToolCall(content);
            if (textCall != null) {
                return LLMResponse.withToolCall(textCall, content);
            }

            return LLMResponse.withContent(content, null);
        } catch (IOException e) {
            log.error("[LLM] 调用失败", e);
            return LLMResponse.withError("抱歉，AI 服务暂时不可用，请稍后重试。", null);
        }
    }

    /**
     * 将 ToolDefinition 列表转换为 OpenAI tools 格式（JSON Schema）。
     */
    private List<Map<String, Object>> convertToolsToOpenAI(List<ToolDefinition> tools) {
        return tools.stream().map(tool -> {
            Map<String, Object> function = new HashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.put("parameters", tool.getParameterSchema() != null
                    ? tool.getParameterSchema() : Map.of("type", "object", "properties", new HashMap<>()));

            Map<String, Object> functionWrapper = new HashMap<>();
            functionWrapper.put("type", "function");
            functionWrapper.put("function", function);
            return functionWrapper;
        }).collect(Collectors.toList());
    }

    /**
     * 将内部 Message 列表转换为 OpenAI API 格式。
     * <p>
     * 格式要求：
     * - assistant(tool_calls): content 可为 null，tool_calls 包含 id/type/function
     * - tool: 必须包含 tool_call_id 和 content
     * - tool 消息必须紧跟在包含对应 tool_calls 的 assistant 消息之后
     */
    private List<Map<String, Object>> convertMessages(List<AgentLoop.Message> messages) {
        // 先过滤并修复消息序列，确保符合 OpenAI API 要求
        List<AgentLoop.Message> sanitized = sanitizeMessages(messages);

        return sanitized.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("role", m.getRole());
            map.put("content", m.getContent());

            // assistant 消息携带 tool_calls 数组
            if ("assistant".equals(m.getRole()) && m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                // 有 tool_calls 时，content 允许为 null（OpenAI 标准）
                List<Map<String, Object>> tcList = m.getToolCalls().stream().map(tc -> {
                    Map<String, Object> tcMap = new HashMap<>();
                    tcMap.put("id", tc.getId() != null ? tc.getId() : "fallback_" + Integer.toHexString(System.identityHashCode(tc)));
                    tcMap.put("type", "function");
                    Map<String, Object> func = new HashMap<>();
                    func.put("name", tc.getToolName());
                    func.put("arguments", JSON.toJSONString(tc.getParams()));
                    tcMap.put("function", func);
                    return tcMap;
                }).collect(Collectors.toList());
                map.put("tool_calls", tcList);
            }

            // tool 消息必须携带 tool_call_id（必填字段）
            if ("tool".equals(m.getRole())) {
                if (m.getToolCallId() != null) {
                    map.put("tool_call_id", m.getToolCallId());
                } else {
                    // 缺少 tool_call_id 会导致 API 报错，生成回退 ID
                    log.warn("[LLM] tool message missing tool_call_id, generating fallback");
                    map.put("tool_call_id", "fallback_" + Integer.toHexString(System.identityHashCode(m)));
                }
            }

            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 清理消息列表，确保符合 OpenAI API 消息序列要求：
     * 1. 每个 tool 消息必须跟在包含 tool_calls 的 assistant 消息之后
     * 2. assistant(tool_calls) 中的每个 tool_call 必须有对应的 tool 消息
     * 3. 不满足以上条件的消息会被转换为安全格式
     */
    private List<AgentLoop.Message> sanitizeMessages(List<AgentLoop.Message> messages) {
        List<AgentLoop.Message> result = new ArrayList<>();
        AgentLoop.Message lastAssistantWithToolCalls = null;
        Set<String> matchedToolCallIds = new HashSet<>();

        for (AgentLoop.Message msg : messages) {
            if ("assistant".equals(msg.getRole()) && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                // 记录这个 assistant(tool_calls) 消息
                lastAssistantWithToolCalls = msg;
                matchedToolCallIds.clear();
                result.add(msg);
            } else if ("tool".equals(msg.getRole())) {
                if (lastAssistantWithToolCalls != null) {
                    // 检查 tool_call_id 是否匹配
                    boolean matched = false;
                    if (msg.getToolCallId() != null) {
                        for (AgentLoop.ToolCall tc : lastAssistantWithToolCalls.getToolCalls()) {
                            if (msg.getToolCallId().equals(tc.getId())) {
                                matchedToolCallIds.add(tc.getId());
                                matched = true;
                                break;
                            }
                        }
                    }
                    if (matched) {
                        result.add(msg);
                    } else {
                        // tool 消息的 tool_call_id 不匹配任何 assistant 的 tool_calls
                        // 转换为 user 消息以避免 API 报错
                        log.warn("[LLM] sanitizing orphaned tool message: tool_call_id={}", msg.getToolCallId());
                        result.add(new AgentLoop.Message("user", "[工具结果] " + msg.getContent()));
                    }
                } else {
                    // 孤立的 tool 消息（没有前导 assistant(tool_calls)）
                    // 转换为 user 消息
                    log.warn("[LLM] sanitizing tool message without preceding assistant(tool_calls)");
                    result.add(new AgentLoop.Message("user", "[工具结果] " + msg.getContent()));
                }
            } else {
                // 非工具消息 — 检查上一个 assistant(tool_calls) 的 tool_calls 是否全部匹配
                if (lastAssistantWithToolCalls != null) {
                    List<AgentLoop.ToolCall> unmatched = lastAssistantWithToolCalls.getToolCalls().stream()
                            .filter(tc -> !matchedToolCallIds.contains(tc.getId()))
                            .collect(Collectors.toList());
                    if (!unmatched.isEmpty()) {
                        // 有未匹配的 tool_calls，需要从 assistant 消息中移除
                        // 保留匹配的 tool_calls，移除未匹配的
                        List<AgentLoop.ToolCall> matched = lastAssistantWithToolCalls.getToolCalls().stream()
                                .filter(tc -> matchedToolCallIds.contains(tc.getId()))
                                .collect(Collectors.toList());
                        if (matched.isEmpty()) {
                            // 所有 tool_calls 都未匹配，转换为普通 assistant 消息
                            lastAssistantWithToolCalls.setToolCalls(null);
                        } else {
                            lastAssistantWithToolCalls.setToolCalls(matched);
                        }
                    }
                }
                lastAssistantWithToolCalls = null;
                matchedToolCallIds.clear();
                result.add(msg);
            }
        }

        // 处理末尾：如果最后一个是 assistant(tool_calls) 但缺少 tool 消息
        if (lastAssistantWithToolCalls != null && !matchedToolCallIds.isEmpty()) {
            // 部分匹配 — 只保留已匹配的 tool_calls
            List<AgentLoop.ToolCall> matched = lastAssistantWithToolCalls.getToolCalls().stream()
                    .filter(tc -> matchedToolCallIds.contains(tc.getId()))
                    .collect(Collectors.toList());
            if (matched.isEmpty()) {
                lastAssistantWithToolCalls.setToolCalls(null);
            } else {
                lastAssistantWithToolCalls.setToolCalls(matched);
            }
        } else if (lastAssistantWithToolCalls != null) {
            // 完全没有匹配的 tool 消息 — 转为普通 assistant
            lastAssistantWithToolCalls.setToolCalls(null);
        }

        return result;
    }

    /**
     * 回退：解析文本 TOOL_CALL: 格式。
     */
    private AgentLoop.ToolCall parseTextToolCall(String content) {
        if (content == null) return null;
        if (content.contains("TOOL_CALL:")) {
            try {
                String jsonStr = content.substring(content.indexOf("TOOL_CALL:") + 10).trim();
                return JSON.parseObject(jsonStr, AgentLoop.ToolCall.class);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 处理 API 错误响应。
     */
    private LLMResponse handleApiError(int statusCode, String body) {
        String message;
        try {
            JSONObject err = JSON.parseObject(body);
            message = err.getJSONObject("error") != null
                    ? err.getJSONObject("error").getString("message")
                    : err.getString("message");
            if (message == null) message = body;
        } catch (Exception e) {
            message = "HTTP " + statusCode;
        }

        return switch (statusCode) {
            case 429 -> LLMResponse.withError("请求过于频繁，请稍后重试。", "rate_limit: " + message);
            case 500, 502, 503 -> LLMResponse.withError("AI 服务暂时不可用，请稍后重试。", "server_error: " + message);
            default -> LLMResponse.withError("AI 服务返回错误: " + message, null);
        };
    }

    /**
     * 简单的 BufferedReader（用于流式读取）。
     */
    private static class BufferedReader implements java.io.Closeable {
        private final java.io.Reader reader;
        BufferedReader(java.io.InputStream inputStream) {
            this.reader = new java.io.InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8);
        }
        String readLine() throws IOException {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1) {
                if (c == '\n') return sb.toString();
                if (c == '\r') continue;
                sb.append((char) c);
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}

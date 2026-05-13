package com.baomihuahua.anticipa.agent.tool;

import com.baomihuahua.anticipa.agent.ToolDefinition;
import com.baomihuahua.anticipa.agent.ToolRegistry;
import com.baomihuahua.anticipa.agent.ToolResult;
import com.baomihuahua.anticipa.agent.discovery.ThreadPoolQueryService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 工具：远程趋势分析与摘要统计。
 * <p>
 * 远程模式下通过 HTTP 调用客户端实例的日志端点获取趋势数据，
 * 返回均值、峰值、拒绝数等统计指标。
 * </p>
 */
public class RemoteAnalyzeTrendsTool {

    private static final Logger log = LoggerFactory.getLogger(RemoteAnalyzeTrendsTool.class);

    private final ToolRegistry toolRegistry;
    private final ThreadPoolQueryService queryService;

    public RemoteAnalyzeTrendsTool(ToolRegistry toolRegistry, ThreadPoolQueryService queryService) {
        this.toolRegistry = toolRegistry;
        this.queryService = queryService;
    }

    @PostConstruct
    public void register() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("thread_pool_id", Map.of(
                "type", "string",
                "description", "线程池 ID（必填）"
        ));
        properties.put("namespace", Map.of(
                "type", "string",
                "description", "Nacos 命名空间（必填）"
        ));
        properties.put("service_name", Map.of(
                "type", "string",
                "description", "服务名称（必填）"
        ));
        properties.put("instance_id", Map.of(
                "type", "string",
                "description", "实例 ID（ip:port），可选。为空则分析服务下所有实例"
        ));
        properties.put("start_time", Map.of(
                "type", "string",
                "description", "开始时间，ISO 格式如 2026-04-27T00:00:00"
        ));
        properties.put("end_time", Map.of(
                "type", "string",
                "description", "结束时间，ISO 格式如 2026-04-28T00:00:00"
        ));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"thread_pool_id", "namespace", "service_name"});

        ToolDefinition tool = ToolDefinition.builder()
                .name("analyze_trends")
                .description("通过 Nacos 定位实例后 HTTP 调用客户端 /logs/{id}/summary，获取线程池趋势摘要（平均/峰值活跃线程、队列使用率、拒绝次数等）。需提供 namespace、service_name；可选 instance_id；可选 start_time/end_time")
                .category(ToolCategory.DIAGNOSE)
                .modification(false)
                .needsApproval(false)
                .concurrencySafe(true)
                .parameterSchema(schema)
                .executor(params -> {
                    String poolId = getParamStr(params, "thread_pool_id");
                    String namespace = getParamStr(params, "namespace");
                    String serviceName = getParamStr(params, "service_name");
                    String instanceId = getParamStr(params, "instance_id");
                    String startTime = getParamStr(params, "start_time");
                    String endTime = getParamStr(params, "end_time");

                    if (poolId == null || poolId.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请指定要分析的线程池 ID")
                                .build();
                    }
                    if (namespace == null || namespace.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请指定 Nacos 命名空间（namespace）")
                                .build();
                    }
                    if (serviceName == null || serviceName.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请指定服务名称（service_name）")
                                .build();
                    }

                    try {
                        Map<String, Object> result = queryService.analyzeTrends(
                                namespace, serviceName, poolId, instanceId,
                                startTime, endTime);

                        int recordCount = result.get("recordCount") instanceof Number
                                ? ((Number) result.get("recordCount")).intValue() : 0;

                        if (recordCount == 0) {
                            return ToolResult.success(
                                    "线程池 [" + poolId + "] 在指定时间范围内无运行日志数据",
                                    result);
                        }

                        // 构建摘要文本
                        StringBuilder sb = new StringBuilder();
                        sb.append("线程池 [").append(poolId).append("] 趋势分析：共 ").append(recordCount).append(" 条记录");
                        if (result.containsKey("avgActiveCount")) sb.append("，活跃线程 平均=").append(result.get("avgActiveCount"));
                        if (result.containsKey("maxActiveCount")) sb.append("/峰值=").append(result.get("maxActiveCount"));
                        if (result.containsKey("avgQueueUsagePercent")) sb.append("，队列使用率 平均=").append(result.get("avgQueueUsagePercent")).append("%");
                        if (result.containsKey("maxQueueUsagePercent")) sb.append("/峰值=").append(result.get("maxQueueUsagePercent")).append("%");
                        if (result.containsKey("totalRejectCount")) sb.append("，总拒绝次数=").append(result.get("totalRejectCount"));

                        return ToolResult.success(sb.toString(), result);
                    } catch (Exception e) {
                        log.error("[RemoteAnalyzeTrendsTool] failed", e);
                        return ToolResult.builder()
                                .success(false)
                                .summary("趋势分析失败：" + e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[RemoteAnalyzeTrendsTool] registered tool: analyze_trends");
    }

    private String getParamStr(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }
}

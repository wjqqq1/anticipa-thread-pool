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
 * AI 工具：通过 HTTP 调用指定实例上的日志存在性接口（check_logs_exists）。
 * <p>
 * 实例维度：仅需业务实例地址（ip:port）与线程池 ID，不经 Nacos 发现。
 * </p>
 */
public class RemoteCheckLogsExistTool {

    private static final Logger log = LoggerFactory.getLogger(RemoteCheckLogsExistTool.class);

    private final ToolRegistry toolRegistry;
    private final ThreadPoolQueryService queryService;

    public RemoteCheckLogsExistTool(ToolRegistry toolRegistry, ThreadPoolQueryService queryService) {
        this.toolRegistry = toolRegistry;
        this.queryService = queryService;
    }

    @PostConstruct
    public void register() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("thread_pool_id", Map.of(
                "type", "string",
                "description", "线程池 ID（必填），可通过 list_thread_pools 或 list_instance_thread_pools 获取"
        ));
        properties.put("instance_address", Map.of(
                "type", "string",
                "description", "业务实例地址（必填），格式 ip:port，如 192.168.1.10:8080；可先通过 list_instances 获取"
        ));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"thread_pool_id", "instance_address"});

        ToolDefinition tool = ToolDefinition.builder()
                .name("check_logs_exists")
                .description("实例维度：经 HTTP 检查指定实例（ip:port）上该线程池是否有历史运行日志。必填 thread_pool_id、instance_address。有日志可继续 query_history；无则按知识库建议")
                .category(ToolCategory.DIAGNOSE)
                .modification(false)
                .needsApproval(false)
                .concurrencySafe(true)
                .parameterSchema(schema)
                .executor(params -> {
                    String poolId = getParamStr(params, "thread_pool_id");
                    String instanceAddress = getParamStr(params, "instance_address");

                    if (poolId == null || poolId.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请指定要检查的线程池 ID")
                                .build();
                    }
                    if (instanceAddress == null || instanceAddress.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请指定实例地址 instance_address（格式 ip:port，如 192.168.1.10:8080）")
                                .build();
                    }

                    try {
                        Map<String, Object> result = queryService.checkLogsExistDirect(poolId, instanceAddress);
                        boolean exists = Boolean.TRUE.equals(result.get("exists"));
                        String message = exists
                                ? "线程池 [" + poolId + "] 存在历史日志数据，可以调用 query_history 获取详细运行指标进行分析"
                                : "线程池 [" + poolId + "] 无历史日志数据，建议直接基于知识库和最佳实践进行调优建议";

                        return ToolResult.success(message, result);
                    } catch (Exception e) {
                        log.error("[RemoteCheckLogsExistTool] failed", e);
                        return ToolResult.builder()
                                .success(false)
                                .summary("检查日志存在性失败：" + e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[RemoteCheckLogsExistTool] registered tool: check_logs_exists");
    }

    private String getParamStr(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }
}

package com.baomihuahua.anticipa.agent.tool;

import com.baomihuahua.anticipa.agent.ToolDefinition;
import com.baomihuahua.anticipa.agent.ToolRegistry;
import com.baomihuahua.anticipa.agent.ToolResult;
import com.baomihuahua.anticipa.agent.discovery.ThreadPoolQueryService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 工具：查询指定实例上的线程池列表及基本信息。
 * <p>
 * 底层依赖 {@link ThreadPoolQueryService#listInstancePoolDetails(String, String, String)}，
 * 通过 HTTP 调用目标实例的 /snapshot 端点获取线程池 ID 及运行时指标摘要。
 * 适用于用户已知实例地址，想查看该实例上有哪些线程池的场景。
 * </p>
 */
public class ListInstanceThreadPoolsTool {

    private static final Logger log = LoggerFactory.getLogger(ListInstanceThreadPoolsTool.class);

    private final ToolRegistry toolRegistry;
    private final ThreadPoolQueryService queryService;

    public ListInstanceThreadPoolsTool(ToolRegistry toolRegistry, ThreadPoolQueryService queryService) {
        this.toolRegistry = toolRegistry;
        this.queryService = queryService;
    }

    @PostConstruct
    public void register() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("namespace", Map.of(
                "type", "string",
                "description", "Nacos 命名空间（必填）。可通过 list_namespaces 工具查看可用命名空间"));
        properties.put("service_name", Map.of(
                "type", "string",
                "description", "服务名称（必填），例如 user-service、order-service 等。可通过 list_services 工具查看可用服务"));
        properties.put("instance_id", Map.of(
                "type", "string",
                "description", "实例 ID（必填），格式为 ip:port，例如 192.168.1.10:8080。可通过 list_instances 工具查看可用实例"));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("namespace", "service_name", "instance_id"));
        schema.put("properties", properties);

        ToolDefinition tool = ToolDefinition.builder()
                .name("list_instance_thread_pools")
                .description("查询指定实例上的线程池列表及基本信息（含核心线程数、最大线程数、活跃线程数、队列使用等）。" +
                        "namespace、service_name 和 instance_id 均为必填参数。适用于用户已知具体实例，想查看该实例上有哪些线程池的场景。" +
                        "如果用户未提供参数，请先使用 list_namespaces → list_services → list_instances 引导用户选择")
                .category(ToolCategory.QUERY)
                .modification(false)
                .needsApproval(false)
                .concurrencySafe(true)
                .parameterSchema(schema)
                .executor(params -> {
                    try {
                        String namespace = params != null && params.get("namespace") != null
                                ? params.get("namespace").toString() : null;
                        String serviceName = params != null && params.get("service_name") != null
                                ? params.get("service_name").toString() : null;
                        String instanceId = params != null && params.get("instance_id") != null
                                ? params.get("instance_id").toString() : null;

                        if (namespace == null || namespace.isEmpty()) {
                            return ToolResult.builder()
                                    .success(false)
                                    .summary("缺少必填参数 namespace，请使用 list_namespaces 工具查看可用命名空间")
                                    .build();
                        }
                        if (serviceName == null || serviceName.isEmpty()) {
                            return ToolResult.builder()
                                    .success(false)
                                    .summary("缺少必填参数 service_name，请使用 list_services 工具查看可用服务")
                                    .build();
                        }
                        if (instanceId == null || instanceId.isEmpty()) {
                            return ToolResult.builder()
                                    .success(false)
                                    .summary("缺少必填参数 instance_id，请使用 list_instances 工具查看可用实例")
                                    .build();
                        }

                        List<Map<String, Object>> poolDetails = queryService.listInstancePoolDetails(namespace, serviceName, instanceId);
                        if (poolDetails.isEmpty()) {
                            return ToolResult.success(
                                    "实例 " + instanceId + " 上未发现任何线程池，请确认实例状态及参数是否正确",
                                    Map.of("pools", List.of(), "count", 0,
                                            "namespace", namespace, "serviceName", serviceName, "instanceId", instanceId));
                        }

                        StringBuilder summary = new StringBuilder();
                        summary.append("实例 ").append(instanceId).append(" 上共发现 ").append(poolDetails.size()).append(" 个线程池：\n");
                        for (Map<String, Object> pool : poolDetails) {
                            summary.append("- ").append(pool.get("poolId"));
                            if (pool.containsKey("corePoolSize")) {
                                summary.append(" (核心=").append(pool.get("corePoolSize"));
                                summary.append(", 最大=").append(pool.get("maximumPoolSize"));
                                summary.append(", 活跃=").append(pool.get("activeCount"));
                                summary.append(", 队列=").append(pool.get("queueSize")).append("/").append(pool.get("queueCapacity"));
                                summary.append(")");
                            }
                            summary.append("\n");
                        }

                        return ToolResult.success(summary.toString().trim(), Map.of(
                                "pools", poolDetails,
                                "count", poolDetails.size(),
                                "namespace", namespace,
                                "serviceName", serviceName,
                                "instanceId", instanceId
                        ));
                    } catch (Exception e) {
                        log.error("[ListInstanceThreadPoolsTool] failed", e);
                        return ToolResult.builder()
                                .success(false)
                                .summary("查询实例线程池列表失败：" + e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[ListInstanceThreadPoolsTool] registered tool: list_instance_thread_pools");
    }
}

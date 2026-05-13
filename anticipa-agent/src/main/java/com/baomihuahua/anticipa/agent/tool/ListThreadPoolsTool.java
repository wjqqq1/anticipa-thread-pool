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
 * AI 工具：远程（Nacos）下列举指定命名空间与服务下的线程池 ID。
 * <p>
 * 通过 {@link ThreadPoolQueryService#listPoolIdsByService(String, String)} 经 Nacos 发现实例并 HTTP 拉取，
 * 仅在 {@code anticipa.agent.nacos.enabled=true} 时注册；本地嵌入模式请用 {@code query_thread_pool} 或本机配置获知池 ID。
 * </p>
 */
public class ListThreadPoolsTool {

    private static final Logger log = LoggerFactory.getLogger(ListThreadPoolsTool.class);

    private final ToolRegistry toolRegistry;
    private final ThreadPoolQueryService queryService;

    public ListThreadPoolsTool(ToolRegistry toolRegistry, ThreadPoolQueryService queryService) {
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

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("namespace", "service_name"));
        schema.put("properties", properties);

        ToolDefinition tool = ToolDefinition.builder()
                .name("list_thread_pools")
                .description("（远程/Nacos）列举指定命名空间与服务下线程池 ID：经 Nacos 发现实例后聚合各实例上的池列表。namespace、service_name 必填；未提供时先用 list_namespaces、list_services，禁止猜测")
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

                        List<String> poolIds = queryService.listPoolIdsByService(namespace, serviceName);
                        if (poolIds.isEmpty()) {
                            return ToolResult.success("命名空间 \"" + namespace + "\" 下服务 \"" + serviceName + "\" 未发现任何线程池，请确认参数是否正确",
                                    Map.of("poolIds", List.of(), "count", 0, "namespace", namespace, "serviceName", serviceName));
                        }

                        String summary = "命名空间 " + namespace + " 服务 " + serviceName + " 共发现 " + poolIds.size() + " 个线程池：" + String.join(", ", poolIds);
                        return ToolResult.success(summary, Map.of("poolIds", poolIds, "count", poolIds.size(), "namespace", namespace, "serviceName", serviceName));
                    } catch (Exception e) {
                        log.error("[ListThreadPoolsTool] failed", e);
                        return ToolResult.builder()
                                .success(false)
                                .summary("查询线程池列表失败：" + e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[ListThreadPoolsTool] registered tool: list_thread_pools");
    }
}

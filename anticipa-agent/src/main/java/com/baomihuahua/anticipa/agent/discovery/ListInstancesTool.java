package com.baomihuahua.anticipa.agent.discovery;

import com.baomihuahua.anticipa.agent.ToolDefinition;
import com.baomihuahua.anticipa.agent.ToolRegistry;
import com.baomihuahua.anticipa.agent.ToolResult;
import com.baomihuahua.anticipa.agent.tool.ToolCategory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 工具：列举指定命名空间和服务下的实例及其线程池。
 * <p>
 * 让 AI 能够发现指定命名空间中服务的运行实例以及每个实例上的线程池列表，
 * 是多实例感知的基础工具。
 * </p>
 */
public class ListInstancesTool {

    private static final Logger log = LoggerFactory.getLogger(ListInstancesTool.class);

    private final ToolRegistry toolRegistry;
    private final InstanceDiscoveryService discoveryService;

    public ListInstancesTool(ToolRegistry toolRegistry, InstanceDiscoveryService discoveryService) {
        this.toolRegistry = toolRegistry;
        this.discoveryService = discoveryService;
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
                .name("list_instances")
                .description("查询指定命名空间和服务的实例信息及其上的线程池列表。namespace 和 service_name 为必填参数，如果用户未提供，请先使用 list_namespaces 和 list_services 工具引导用户选择，不要猜测或编造")
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

                        List<InstanceInfo> instances = discoveryService.discoverInstances(namespace, serviceName);

                        if (instances.isEmpty()) {
                            return ToolResult.success("命名空间 \"" + namespace + "\" 下未找到服务 \"" + serviceName + "\" 的实例，请确认参数是否正确",
                                    Map.of("instances", List.of(), "namespace", namespace, "serviceName", serviceName));
                        }

                        List<Map<String, Object>> instanceList = instances.stream().map(inst -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("instanceId", inst.getInstanceId());
                            m.put("appName", inst.getAppName());
                            m.put("namespace", inst.getNamespace());
                            m.put("host", inst.getHost());
                            m.put("port", inst.getPort());
                            m.put("status", inst.getStatus());
                            m.put("threadPoolIds", inst.getThreadPoolIds());
                            return m;
                        }).collect(Collectors.toList());

                        StringBuilder summary = new StringBuilder();
                        summary.append("命名空间 ").append(namespace).append(" 服务 ").append(serviceName)
                                .append(" 共发现 ").append(instances.size()).append(" 个实例：\n");
                        for (InstanceInfo inst : instances) {
                            summary.append("- ").append(inst.getInstanceId())
                                    .append(" (").append(inst.getStatus()).append(")")
                                    .append(" 线程池: [")
                                    .append(String.join(", ", inst.getThreadPoolIds()))
                                    .append("]\n");
                        }

                        return ToolResult.success(summary.toString().trim(), Map.of(
                                "instances", instanceList,
                                "count", instances.size(),
                                "namespace", namespace,
                                "serviceName", serviceName
                        ));
                    } catch (Exception e) {
                        log.error("[ListInstancesTool] failed", e);
                        return ToolResult.builder()
                                .success(false)
                                .summary("查询实例列表失败：" + e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[ListInstancesTool] registered tool: list_instances");
    }
}

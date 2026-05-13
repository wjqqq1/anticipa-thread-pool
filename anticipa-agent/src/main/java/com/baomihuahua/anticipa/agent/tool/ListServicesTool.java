package com.baomihuahua.anticipa.agent.tool;

import com.baomihuahua.anticipa.agent.ToolDefinition;
import com.baomihuahua.anticipa.agent.ToolRegistry;
import com.baomihuahua.anticipa.agent.ToolResult;
import com.baomihuahua.anticipa.agent.discovery.InstanceDiscoveryService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 工具：列出指定 Nacos 命名空间下的所有服务。
 * <p>
 * 探索线程池层级结构的第二步：先使用 list_namespaces 查看命名空间，
 * 再使用本工具查看服务列表。
 * </p>
 */
public class ListServicesTool {

    private static final Logger log = LoggerFactory.getLogger(ListServicesTool.class);

    private final ToolRegistry toolRegistry;
    private final InstanceDiscoveryService discoveryService;

    public ListServicesTool(ToolRegistry toolRegistry, InstanceDiscoveryService discoveryService) {
        this.toolRegistry = toolRegistry;
        this.discoveryService = discoveryService;
    }

    @PostConstruct
    public void register() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("namespace", Map.of(
                "type", "string",
                "description", "Nacos 命名空间（必填）。可通过 list_namespaces 工具查看可用命名空间"
        ));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("namespace"));
        schema.put("properties", properties);

        ToolDefinition tool = ToolDefinition.builder()
                .name("list_services")
                .description("列出指定 Nacos 命名空间下的所有服务。这是探索线程池层级结构的第二步：先使用 list_namespaces 查看命名空间，再使用本工具查看服务列表。如果用户未提供命名空间，请先使用 list_namespaces 工具引导选择")
                .category(ToolCategory.QUERY)
                .modification(false)
                .needsApproval(false)
                .concurrencySafe(true)
                .parameterSchema(schema)
                .executor(params -> {
                    try {
                        String namespace = params != null && params.get("namespace") != null
                                ? params.get("namespace").toString() : null;

                        if (namespace == null || namespace.isEmpty()) {
                            return ToolResult.builder()
                                    .success(false)
                                    .summary("缺少必填参数 namespace，请使用 list_namespaces 工具查看可用命名空间")
                                    .build();
                        }

                        List<String> services = discoveryService.listServiceNames(namespace);
                        if (services == null || services.isEmpty()) {
                            return ToolResult.success("命名空间 \"" + namespace + "\" 下未发现任何服务，请确认命名空间是否正确",
                                    Map.of("services", List.of(), "count", 0, "namespace", namespace));
                        }

                        String summary = "命名空间 " + namespace + " 下共发现 " + services.size() + " 个服务：" + String.join(", ", services);
                        return ToolResult.success(summary, Map.of("services", services, "count", services.size(), "namespace", namespace));
                    } catch (Exception e) {
                        log.error("[ListServicesTool] failed", e);
                        return ToolResult.builder()
                                .success(false)
                                .summary("查询服务列表失败：" + e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[ListServicesTool] registered tool: list_services");
    }
}

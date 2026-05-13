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
 * AI 工具：列出当前可用的 Nacos 命名空间。
 * <p>
 * 探索线程池层级结构的第一步：Namespace -> Service -> Instance -> ThreadPool。
 * </p>
 */
public class ListNamespacesTool {

    private static final Logger log = LoggerFactory.getLogger(ListNamespacesTool.class);

    private final ToolRegistry toolRegistry;
    private final InstanceDiscoveryService discoveryService;

    public ListNamespacesTool(ToolRegistry toolRegistry, InstanceDiscoveryService discoveryService) {
        this.toolRegistry = toolRegistry;
        this.discoveryService = discoveryService;
    }

    @PostConstruct
    public void register() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", new HashMap<>());

        ToolDefinition tool = ToolDefinition.builder()
                .name("list_namespaces")
                .description("列出当前可用的 Nacos 命名空间。这是探索线程池层级结构的第一步：Namespace -> Service -> Instance -> ThreadPool。当用户不确定要查询哪个命名空间时，请先调用此工具")
                .category(ToolCategory.QUERY)
                .modification(false)
                .needsApproval(false)
                .concurrencySafe(true)
                .parameterSchema(schema)
                .executor(params -> {
                    try {
                        List<String> namespaces = discoveryService.listNamespaces();
                        if (namespaces == null || namespaces.isEmpty()) {
                            return ToolResult.success("当前未配置任何命名空间",
                                    Map.of("namespaces", List.of(), "count", 0));
                        }

                        String summary = "当前可用的 Nacos 命名空间（" + namespaces.size() + " 个）：" + String.join(", ", namespaces);
                        return ToolResult.success(summary, Map.of("namespaces", namespaces, "count", namespaces.size()));
                    } catch (Exception e) {
                        log.error("[ListNamespacesTool] failed", e);
                        return ToolResult.builder()
                                .success(false)
                                .summary("查询命名空间列表失败：" + e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[ListNamespacesTool] registered tool: list_namespaces");
    }
}

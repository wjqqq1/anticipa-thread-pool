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
 * AI 工具：列出指定 Nacos 命名空间下的配置文件。
 * <p>
 * 用于查找 update_thread_pool_config 工具所需的 dataId 参数。
 * 如果用户想修改配置文件但不知道文件名，请先使用本工具列出可用的配置文件。
 * </p>
 */
public class ListNacosConfigsTool {

    private static final Logger log = LoggerFactory.getLogger(ListNacosConfigsTool.class);

    private final ToolRegistry toolRegistry;
    private final ThreadPoolQueryService queryService;

    public ListNacosConfigsTool(ToolRegistry toolRegistry, ThreadPoolQueryService queryService) {
        this.toolRegistry = toolRegistry;
        this.queryService = queryService;
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
                .name("list_nacos_configs")
                .description("列出指定 Nacos 命名空间下的所有配置文件（dataId）。用于查找 update_thread_pool_config 工具所需的 dataId 参数。如果用户想修改配置文件但不知道文件名，请先使用本工具列出可用的配置文件")
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

                        List<String> configs = queryService.listNacosConfigs(namespace);
                        if (configs == null || configs.isEmpty()) {
                            return ToolResult.success("命名空间 \"" + namespace + "\" 下未发现任何配置文件，请确认命名空间是否正确",
                                    Map.of("configs", List.of(), "count", 0, "namespace", namespace));
                        }

                        String summary = "命名空间 " + namespace + " 下共发现 " + configs.size() + " 个配置文件：" + String.join(", ", configs);
                        return ToolResult.success(summary, Map.of("configs", configs, "count", configs.size(), "namespace", namespace));
                    } catch (Exception e) {
                        log.error("[ListNacosConfigsTool] failed", e);
                        return ToolResult.builder()
                                .success(false)
                                .summary("查询配置文件列表失败：" + e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[ListNacosConfigsTool] registered tool: list_nacos_configs");
    }
}

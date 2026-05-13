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
 * AI 工具：修改 Nacos 配置文件中的线程池参数（持久化，影响所有使用该配置的实例）。
 * <p>
 * 读取 Nacos 中指定的配置文件 YAML，找到目标线程池定义，修改参数后发布回 Nacos。
 * 所有使用该配置的客户端实例将通过 Nacos 监听器自动刷新。
 * 通过 {@link ThreadPoolQueryService} 抽象层执行，兼容本地和远程操作。
 * </p>
 */
public class UpdateThreadPoolConfigTool {

    private static final Logger log = LoggerFactory.getLogger(UpdateThreadPoolConfigTool.class);

    private final ToolRegistry toolRegistry;
    private final ThreadPoolQueryService queryService;

    public UpdateThreadPoolConfigTool(ToolRegistry toolRegistry, ThreadPoolQueryService queryService) {
        this.toolRegistry = toolRegistry;
        this.queryService = queryService;
    }

    @PostConstruct
    public void register() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("thread_pool_id", Map.of(
                "type", "string",
                "description", "线程池 ID"
        ));
        properties.put("namespace", Map.of(
                "type", "string",
                "description", "Nacos 命名空间（必填）。可通过 list_namespaces 工具查看可用命名空间"
        ));
        properties.put("service_name", Map.of(
                "type", "string",
                "description", "Nacos 服务名（必填）。用于确认服务与配置文件的对应关系，可通过 list_services 工具查看可用服务"
        ));
        properties.put("data_id", Map.of(
                "type", "string",
                "description", "Nacos 配置文件 dataId，例如 order-service.yaml（必填）。如果不确定 dataId，请先使用 list_nacos_configs 工具查看可用的配置文件列表"
        ));
        properties.put("group", Map.of(
                "type", "string",
                "description", "Nacos 配置分组（可选，默认 DEFAULT_GROUP）"
        ));
        properties.put("core_pool_size", Map.of(
                "type", "integer",
                "description", "核心线程数（可选）"
        ));
        properties.put("maximum_pool_size", Map.of(
                "type", "integer",
                "description", "最大线程数（可选）"
        ));
        properties.put("queue_capacity", Map.of(
                "type", "integer",
                "description", "队列容量（可选）"
        ));
        properties.put("keep_alive_seconds", Map.of(
                "type", "integer",
                "description", "线程存活时间，单位秒（可选）"
        ));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"namespace", "service_name", "thread_pool_id", "data_id"});

        ToolDefinition tool = ToolDefinition.builder()
                .name("update_thread_pool_config")
                .description("修改 Nacos 配置文件中的线程池参数（持久化，影响所有使用该配置的实例）。此操作会修改配置源文件，所有客户端实例将自动刷新。需提供 namespace、service_name 和 data_id 以定位配置文件。如果不确定 dataId，请先使用 list_nacos_configs 工具查看可用配置文件。注意：此操作需经过审批，且为高风险操作")
                .category(ToolCategory.ADJUST)
                .modification(true)
                .needsApproval(true)
                .concurrencySafe(false)
                .parameterSchema(schema)
                .executor(params -> {
                    String poolId = getParamStr(params, "thread_pool_id");
                    String namespace = getParamStr(params, "namespace");
                    String serviceName = getParamStr(params, "service_name");
                    String dataId = getParamStr(params, "data_id");
                    String group = getParamStr(params, "group");

                    // 参数校验
                    if (namespace == null || namespace.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请指定 Nacos 命名空间（namespace），可使用 list_namespaces 工具查看")
                                .build();
                    }
                    if (serviceName == null || serviceName.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请指定 Nacos 服务名（service_name），可使用 list_services 工具查看")
                                .build();
                    }
                    if (poolId == null || poolId.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请指定要修改的线程池 ID")
                                .build();
                    }
                    if (dataId == null || dataId.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请指定 Nacos 配置文件 dataId。如果不确定，请先使用 list_nacos_configs 工具查看可用的配置文件列表")
                                .build();
                    }

                    // 构造修改参数
                    Map<String, Object> configParams = new HashMap<>();
                    Integer coreSize = getParamInt(params, "core_pool_size");
                    if (coreSize != null) configParams.put("corePoolSize", coreSize);
                    Integer maxSize = getParamInt(params, "maximum_pool_size");
                    if (maxSize != null) configParams.put("maximumPoolSize", maxSize);
                    Integer queueCap = getParamInt(params, "queue_capacity");
                    if (queueCap != null) configParams.put("queueCapacity", queueCap);
                    Integer keepAlive = getParamInt(params, "keep_alive_seconds");
                    if (keepAlive != null) configParams.put("keepAliveTime", keepAlive.longValue());

                    if (configParams.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("未指定要修改的参数，请提供至少一个调整项")
                                .build();
                    }

                    // 安全校验
                    if (coreSize != null && coreSize <= 0) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("核心线程数必须大于 0")
                                .build();
                    }
                    if (maxSize != null && maxSize <= 0) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("最大线程数必须大于 0")
                                .build();
                    }
                    if (maxSize != null && maxSize > 500) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("最大线程数不能超过 500")
                                .build();
                    }
                    if (queueCap != null && queueCap <= 0) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("队列容量必须大于 0")
                                .build();
                    }

                    // 调用服务层
                    Map<String, Object> result = queryService.updateConfig(namespace, dataId, group, poolId, configParams);
                    if (result == null) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("配置更新失败")
                                .build();
                    }

                    if (result.containsKey("error")) {
                        return ToolResult.builder()
                                .success(false)
                                .summary(String.valueOf(result.get("error")))
                                .build();
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> changes = (Map<String, Object>) result.get("changes");
                    int affectedCount = result.get("affectedInstanceCount") instanceof Number
                            ? ((Number) result.get("affectedInstanceCount")).intValue() : 0;

                    String summary = "已修改 Nacos 配置 [" + dataId + "]，线程池 [" + poolId + "] 参数已更新"
                            + (affectedCount > 0 ? "，约 " + affectedCount + " 个实例将自动刷新" : "")
                            + "。变更项：" + changes.entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");

                    return ToolResult.success(summary, result);
                })
                .build();

        toolRegistry.register(tool);
        log.info("[UpdateThreadPoolConfigTool] registered tool: update_thread_pool_config");
    }

    private String getParamStr(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }

    private Integer getParamInt(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

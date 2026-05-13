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
 * AI 工具：调整指定实例的线程池运行时参数（临时生效，重启后恢复）。
 * <p>
 * 只需提供目标实例地址（ip:port）和线程池 ID 即可发起调整。
 * 如果用户不知道实例地址或线程池 ID，可借助 namespace + service_name
 * 通过 list_namespaces → list_services → list_instances → list_thread_pools 逐步查找。
 * 调整成功后由实例端发送钉钉变更通知。
 * </p>
 */
public class AdjustThreadPoolTool {

    private static final Logger log = LoggerFactory.getLogger(AdjustThreadPoolTool.class);

    private final ToolRegistry toolRegistry;
    private final ThreadPoolQueryService queryService;

    public AdjustThreadPoolTool(ToolRegistry toolRegistry, ThreadPoolQueryService queryService) {
        this.toolRegistry = toolRegistry;
        this.queryService = queryService;
    }

    @PostConstruct
    public void register() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("instance_id", Map.of(
                "type", "string",
                "description", "目标实例地址（必填），格式为 ip:port，例如 192.168.1.10:8080。如果不知道实例地址，可通过 list_namespaces → list_services → list_instances 逐步查找"
        ));
        properties.put("thread_pool_id", Map.of(
                "type", "string",
                "description", "线程池 ID（必填）。如果不知道线程池 ID，可通过 list_thread_pools 工具查看指定服务下的线程池列表"
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
        schema.put("required", new String[]{"instance_id", "thread_pool_id"});

        ToolDefinition tool = ToolDefinition.builder()
                .name("adjust_thread_pool")
                .description("调整指定实例的线程池运行时参数（临时生效，重启后恢复）。" +
                        "只需提供 instance_id（ip:port）和 thread_pool_id 即可调用，工具直接调用目标实例 HTTP 接口完成调整。" +
                        "调整成功后实例端会自动发送钉钉变更通知。如需修改所有实例的持久化配置，请使用 update_thread_pool_config。" +
                        "注意：调整操作需经过审批")
                .category(ToolCategory.ADJUST)
                .modification(true)
                .needsApproval(true)
                .concurrencySafe(false)
                .parameterSchema(schema)
                .executor(params -> {
                    String instanceId = getParamStr(params, "instance_id");
                    String poolId = getParamStr(params, "thread_pool_id");

                    // 必填参数校验
                    if (instanceId == null || instanceId.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("缺少必填参数 instance_id（目标实例地址，格式 ip:port）。可通过 list_namespaces → list_services → list_instances 逐步查找实例地址。如需修改所有实例配置，请使用 update_thread_pool_config")
                                .build();
                    }
                    if (poolId == null || poolId.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("缺少必填参数 thread_pool_id。可通过 list_thread_pools 工具查看指定服务下的线程池列表")
                                .build();
                    }

                    // 构造调整参数
                    Map<String, Object> adjustParams = new HashMap<>();
                    Integer coreSize = getParamInt(params, "core_pool_size");
                    if (coreSize != null) adjustParams.put("corePoolSize", coreSize);
                    Integer maxSize = getParamInt(params, "maximum_pool_size");
                    if (maxSize != null) adjustParams.put("maximumPoolSize", maxSize);
                    Integer keepAlive = getParamInt(params, "keep_alive_seconds");
                    if (keepAlive != null) adjustParams.put("keepAliveTime", keepAlive.longValue());
                    Integer queueCap = getParamInt(params, "queue_capacity");
                    if (queueCap != null) adjustParams.put("queueCapacity", queueCap);

                    if (adjustParams.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("未指定要调整的参数，请提供至少一个调整项（core_pool_size、maximum_pool_size、queue_capacity、keep_alive_seconds）")
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

                    // 直接调用远程实例 HTTP 接口（跳过 Nacos 校验，无需 namespace/service_name）
                    Map<String, Object> result = queryService.adjustPool(null, null, poolId, instanceId, adjustParams);
                    if (result == null) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("实例 " + instanceId + " 不可达，或线程池 " + poolId + " 不存在")
                                .build();
                    }

                    if (result.containsKey("error")) {
                        return ToolResult.builder()
                                .success(false)
                                .summary(String.valueOf(result.get("error")))
                                .build();
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) result.get("data");
                    String summary;
                    if (data != null && data.get("adjustedFields") != null) {
                        Object adjustedFields = data.get("adjustedFields");
                        Object before = data.get("before");
                        Object after = data.get("after");
                        String changesSummary = adjustedFields.toString();
                        if (before instanceof Map && after instanceof Map) {
                            Map<String, Object> beforeMap = (Map<String, Object>) before;
                            Map<String, Object> afterMap = (Map<String, Object>) after;
                            changesSummary = ((List<String>) adjustedFields).stream()
                                    .filter(f -> beforeMap.containsKey(f) || afterMap.containsKey(f))
                                    .map(f -> f + ": " + beforeMap.getOrDefault(f, "?") + " -> " + afterMap.getOrDefault(f, "?"))
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse(changesSummary);
                        }
                        summary = "线程池 [" + poolId + "] 在实例 " + instanceId + " 上已调整：" + changesSummary;
                    } else {
                        summary = "线程池 [" + poolId + "] 在实例 " + instanceId + " 上已调整";
                    }
                    summary += "（调整后实例端已自动发送钉钉变更通知）";

                    return ToolResult.success(summary, result);
                })
                .build();

        toolRegistry.register(tool);
        log.info("[AdjustThreadPoolTool] registered tool: adjust_thread_pool");
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

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
 * AI 工具：查询指定线程池的运行时状态。
 * <p>
 * 两种用法：（1）直连：{@code instance_id}（ip:port）+ {@code thread_pool_id} 即可；
 * （2）经 Nacos：{@code namespace} + {@code service_name} + {@code thread_pool_id}（未提供 {@code instance_id} 时可能多实例聚合）。
 * </p>
 */
public class QueryThreadPoolTool {

    private static final Logger log = LoggerFactory.getLogger(QueryThreadPoolTool.class);

    private final ToolRegistry toolRegistry;
    private final ThreadPoolQueryService queryService;

    public QueryThreadPoolTool(ToolRegistry toolRegistry, ThreadPoolQueryService queryService) {
        this.toolRegistry = toolRegistry;
        this.queryService = queryService;
    }

    @PostConstruct
    public void register() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("service_name", Map.of(
                "type", "string",
                "description", "Nacos 服务名。仅在未提供 instance_id、需要通过注册中心发现实例时必填；用户已给出 ip:port（instance_id）时不要索要此参数"
        ));
        properties.put("thread_pool_id", Map.of(
                "type", "string",
                "description", "线程池 ID（必填），与业务中配置的线程池标识一致"
        ));
        properties.put("namespace", Map.of(
                "type", "string",
                "description", "Nacos 命名空间。仅在经 Nacos 查询（未提供 instance_id）时必填，可用 list_namespaces；用户已提供 ip:port 直连时不要索要"
        ));
        properties.put("instance_id", Map.of(
                "type", "string",
                "description", "目标实例地址（直连模式）。格式 ip:port，例如 192.168.1.10:8080；也支持 serviceName:ip:port。与 thread_pool_id 同时提供时即可查询，无需 namespace/service_name。留空则经 Nacos 按 namespace+service_name 发现实例（可多实例聚合）"
        ));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"thread_pool_id"});

        ToolDefinition tool = ToolDefinition.builder()
                .name("query_thread_pool")
                .description("查询指定线程池的运行时指标（核心/最大线程、活跃数、队列占用、已完成任务等）。"
                        + "若用户已提供实例地址 ip:port（或 serviceName:ip:port）与线程池 ID，请将地址填入 instance_id、将线程池 ID 填入 thread_pool_id 并直接调用，不要向用户索要 namespace 或 service_name。"
                        + "仅当用户未提供任何实例地址、需要经 Nacos 发现服务实例时，才要求 namespace 与 service_name（namespace 可用 list_namespaces），且不要猜测服务名。"
                        + "未指定 instance_id 时可能返回该服务下多实例的聚合结果。")
                .category(ToolCategory.QUERY)
                .modification(false)
                .needsApproval(false)
                .concurrencySafe(true)
                .parameterSchema(schema)
                .executor(params -> {
                    String poolId = getParamStr(params, "thread_pool_id");
                    if (poolId == null) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请指定要查询的线程池 ID（thread_pool_id）")
                                .build();
                    }

                    String serviceName = getParamStr(params, "service_name");
                    String namespace = getParamStr(params, "namespace");
                    String instanceId = getParamStr(params, "instance_id");

                    final Map<String, Object> data;
                    final boolean direct = instanceId != null;
                    if (direct) {
                        data = queryService.queryPoolMetricsDirect(poolId, instanceId);
                    } else {
                        if (serviceName == null) {
                            return ToolResult.builder()
                                    .success(false)
                                    .summary("请提供 instance_id（ip:port）以直连查询，或提供 service_name（及 namespace）经 Nacos 查询；不要编造服务名")
                                    .build();
                        }
                        if (namespace == null) {
                            return ToolResult.builder()
                                    .success(false)
                                    .summary("经 Nacos 查询时 namespace 为必填（可用 list_namespaces）。若用户已知实例地址，应使用 instance_id + thread_pool_id 直连，无需 namespace")
                                    .build();
                        }
                        data = queryService.queryPoolMetrics(namespace, serviceName, poolId, null);
                    }

                    if (data == null) {
                        if (direct) {
                            return ToolResult.builder()
                                    .success(false)
                                    .summary("直连查询无结果：请确认 instance_id 为 ip:port 或 serviceName:ip:port、目标可达且 thread_pool_id 正确")
                                    .build();
                        }
                        List<String> availablePools = namespace != null
                                ? queryService.listPoolIdsByService(namespace, serviceName)
                                : queryService.listPoolIdsByService(serviceName);
                        String poolHint = availablePools.isEmpty()
                                ? "该服务下未发现线程池，请确认命名空间、服务名与网络是否可达"
                                : "当前可用的线程池: " + String.join(", ", availablePools);
                        return ToolResult.builder()
                                .success(false)
                                .summary("未找到线程池: " + poolId + "，" + poolHint)
                                .build();
                    }

                    String summary = String.format(
                            "线程池 [%s] 状态：core=%s, max=%s, active=%s, pool=%s, queue=%s/%s (%s%%), completed=%s",
                            poolId,
                            data.get("corePoolSize"),
                            data.get("maximumPoolSize"),
                            data.get("activeCount"),
                            data.get("poolSize"),
                            data.get("queueSize"),
                            data.get("queueCapacity"),
                            data.get("queueUsagePercent"),
                            data.get("completedTaskCount")
                    );

                    return ToolResult.success(summary, data);
                })
                .build();

        toolRegistry.register(tool);
        log.info("[QueryThreadPoolTool] registered tool: query_thread_pool");
    }

    private String getParamStr(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null) {
            return null;
        }
        String s = val.toString().trim();
        return s.isEmpty() ? null : s;
    }
}

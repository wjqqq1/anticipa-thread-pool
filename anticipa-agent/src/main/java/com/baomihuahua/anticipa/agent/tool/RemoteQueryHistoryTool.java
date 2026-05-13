package com.baomihuahua.anticipa.agent.tool;

import com.baomihuahua.anticipa.agent.ToolDefinition;
import com.baomihuahua.anticipa.agent.ToolRegistry;
import com.baomihuahua.anticipa.agent.ToolResult;
import com.baomihuahua.anticipa.agent.config.AgentProperties;
import com.baomihuahua.anticipa.agent.discovery.ThreadPoolQueryService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 工具：query_history — 直连某实例上某线程池的运行日志（HTTP）。
 * <p>
 * 维度为「实例 + 线程池」；当前以按条数（LIMIT）拉取为主。追问用户期望条数时须说明服务端配置的允许区间；
 * 执行时若 limit 不在该区间内则失败并提示，由用户改在范围内再查。
 * </p>
 */
public class RemoteQueryHistoryTool {

    private static final Logger log = LoggerFactory.getLogger(RemoteQueryHistoryTool.class);

    private final ToolRegistry toolRegistry;
    private final ThreadPoolQueryService queryService;
    private final AgentProperties agentProperties;

    public RemoteQueryHistoryTool(ToolRegistry toolRegistry, ThreadPoolQueryService queryService,
                                   AgentProperties agentProperties) {
        this.toolRegistry = toolRegistry;
        this.queryService = queryService;
        this.agentProperties = agentProperties;
    }

    @PostConstruct
    public void register() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("thread_pool_id", Map.of(
                "type", "string",
                "description", "目标实例上的线程池 ID（必填）"
        ));
        properties.put("instance_ip", Map.of(
                "type", "string",
                "description", "实例 IP（必填），IPv4，如 192.168.1.10"
        ));
        properties.put("instance_port", Map.of(
                "type", "integer",
                "description", "实例 HTTP 端口（必填），如 8080"
        ));
        properties.put("query_mode", Map.of(
                "type", "string",
                "enum", new String[]{"TIME_RANGE", "LIMIT"},
                "description", "查询方式（必填）。当前主要使用 LIMIT：在实例默认时间窗内（未传起止时间时一般为最近约30分钟）按条数拉取，须传 limit 且须在服务端配置的条数闭区间内。"
                        + "可选 TIME_RANGE：按起止时间须同时提供 start_time、end_time；若同时传正整数 limit 则同样受上述条数区间约束。"
        ));
        properties.put("start_time", Map.of(
                "type", "string",
                "description", "query_mode=TIME_RANGE 时必填。LIMIT 下可选：若与 end_time 同时提供则在该时间窗内取至多 limit 条。支持 ISO-8601 或本地时间"
        ));
        properties.put("end_time", Map.of(
                "type", "string",
                "description", "query_mode=TIME_RANGE 时必填；LIMIT 下与 start_time 成对可选，格式同 start_time"
        ));
        properties.put("limit", Map.of(
                "type", "integer",
                "description", "query_mode=LIMIT 时必填为正整数，且必须落在 anticipa.agent.history-query-limit-min 与 history-query-limit-max 所配置的闭区间内（默认 50–100），否则工具会失败并提示用户；"
                        + "向用户追问条数时须明确告知当前环境允许的整数范围。TIME_RANGE 下若提供正整数 limit 表示最多条数，同样须在上述闭区间内。"
        ));
        properties.put("aggregation", Map.of(
                "type", "string",
                "enum", new String[]{"RAW", "MINUTE", "HOUR"},
                "description", "聚合：RAW/MINUTE/HOUR，默认 RAW（与实例端接口一致）"
        ));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"thread_pool_id", "instance_ip", "instance_port", "query_mode"});

        ToolDefinition tool = ToolDefinition.builder()
                .name("query_history")
                .description("查询「指定实例上、指定线程池」的历史运行日志（HTTP 直连实例）。"
                        + "当前以按条数查询为主：query_mode=LIMIT，在短时间窗内拉取至多 limit 条；追问用户需要多少条时，必须说明本环境允许的条数闭区间（anticipa.agent.history-query-limit-min 至 history-query-limit-max，默认 50–100），"
                        + "且传入的 limit 必须落在该区间内，否则执行失败并需提示用户改在范围内再试。可选 TIME_RANGE 按起止时间拉取；若带 limit 条数上限则同样受该闭区间约束。"
                        + "必填：thread_pool_id、instance_ip、instance_port、query_mode；不要猜测 IP/端口。")
                .category(ToolCategory.DIAGNOSE)
                .modification(false)
                .needsApproval(false)
                .concurrencySafe(true)
                .parameterSchema(schema)
                .executor(params -> {
                    String poolId = getParamStr(params, "thread_pool_id");
                    String instanceIp = getParamStr(params, "instance_ip");
                    int instancePort = getParamInt(params, "instance_port", -1);
                    String queryMode = getParamStr(params, "query_mode");
                    String startTime = getParamStr(params, "start_time");
                    String endTime = getParamStr(params, "end_time");
                    int limitParam = getParamInt(params, "limit", 0);
                    String aggregation = getParamStr(params, "aggregation");
                    if (aggregation == null || aggregation.isEmpty()) {
                        aggregation = "RAW";
                    }

                    if (poolId == null || poolId.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请指定 thread_pool_id（实例上的线程池 ID）")
                                .build();
                    }
                    if (instanceIp == null || instanceIp.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请指定 instance_ip")
                                .build();
                    }
                    if (instancePort <= 0 || instancePort > 65535) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请指定合法的 instance_port（1–65535）")
                                .build();
                    }
                    if (queryMode == null || queryMode.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请先选择 query_mode：优先 LIMIT（按条数）；可选 TIME_RANGE（按起止时间）。并向用户说明含义后再调用")
                                .build();
                    }

                    String mode = queryMode.trim().toUpperCase();
                    String instanceId = instanceIp.trim() + ":" + instancePort;

                    String startArg = "";
                    String endArg = "";
                    int limitArg;

                    if ("TIME_RANGE".equals(mode)) {
                        if (startTime == null || startTime.isEmpty() || endTime == null || endTime.isEmpty()) {
                            return ToolResult.builder()
                                    .success(false)
                                    .summary("query_mode=TIME_RANGE 时必须同时提供 start_time 与 end_time；请先向用户确认要分析的时间段")
                                    .build();
                        }
                        startArg = startTime;
                        endArg = endTime;
                        if (limitParam > 0) {
                            String limitErr = historyLimitOutOfRangeMessage(limitParam);
                            if (limitErr != null) {
                                return ToolResult.builder().success(false).summary(limitErr).build();
                            }
                            limitArg = limitParam;
                        } else {
                            limitArg = 0;
                        }
                    } else if ("LIMIT".equals(mode)) {
                        if (limitParam <= 0) {
                            return ToolResult.builder()
                                    .success(false)
                                    .summary(limitRangeHintForUser()
                                            + " query_mode=LIMIT 时必须提供正整数 limit；未传 start/end 时实例端通常使用默认最近时间窗。")
                                    .build();
                        }
                        String limitErr = historyLimitOutOfRangeMessage(limitParam);
                        if (limitErr != null) {
                            return ToolResult.builder().success(false).summary(limitErr).build();
                        }
                        limitArg = limitParam;
                        if (startTime != null && !startTime.isEmpty() && endTime != null && !endTime.isEmpty()) {
                            startArg = startTime;
                            endArg = endTime;
                        }
                    } else {
                        return ToolResult.builder()
                                .success(false)
                                .summary("query_mode 只能是 TIME_RANGE 或 LIMIT")
                                .build();
                    }

                    try {
                        Map<String, Object> result = queryService.queryHistoryDirect(
                                poolId, instanceId,
                                startArg, endArg,
                                aggregation, limitArg);

                        if (result.get("error") != null) {
                            return ToolResult.builder()
                                    .success(false)
                                    .summary("查询失败：" + result.get("error"))
                                    .data(result)
                                    .build();
                        }

                        int recordCount = result.get("recordCount") instanceof Number
                                ? ((Number) result.get("recordCount")).intValue() : 0;

                        if (recordCount == 0) {
                            return ToolResult.success(
                                    "实例 " + instanceId + " 上线程池 [" + poolId + "] 在条件下无运行日志数据",
                                    result);
                        }

                        String summary = String.format(
                                "实例 %s 线程池 [%s]：共 %d 条历史记录（mode=%s）",
                                instanceId, poolId, recordCount, mode);

                        return ToolResult.success(summary, result);
                    } catch (Exception e) {
                        log.error("[RemoteQueryHistoryTool] failed", e);
                        return ToolResult.builder()
                                .success(false)
                                .summary("查询历史数据失败：" + e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[RemoteQueryHistoryTool] registered tool: query_history");
    }

    private int[] normalizedHistoryLimitBounds() {
        int min = agentProperties.getHistoryQueryLimitMin();
        int max = agentProperties.getHistoryQueryLimitMax();
        if (min > max) {
            int t = min;
            min = max;
            max = t;
        }
        min = Math.max(1, min);
        max = Math.max(min, max);
        return new int[]{min, max};
    }

    /**
     * @return 若 limit 不在配置的闭区间内则返回面向用户/模型的失败说明；在区间内返回 null。
     */
    private String historyLimitOutOfRangeMessage(int requested) {
        int[] b = normalizedHistoryLimitBounds();
        int min = b[0];
        int max = b[1];
        if (requested >= min && requested <= max) {
            return null;
        }
        return String.format(
                "limit=%d 不在本环境允许的条数范围 [%d, %d] 内（配置项 anticipa.agent.history-query-limit-min / history-query-limit-max）。"
                        + "请向用户说明该范围并请其在区间内指定整数条数后重试。",
                requested, min, max);
    }

    private String limitRangeHintForUser() {
        int[] b = normalizedHistoryLimitBounds();
        return String.format("追问用户需要拉取多少条时，须告知允许范围为 %d–%d 条（闭区间）。", b[0], b[1]);
    }

    private String getParamStr(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString().trim() : null;
    }

    private int getParamInt(Map<String, Object> params, String key, int defaultVal) {
        Object val = params.get(key);
        if (val == null) {
            return defaultVal;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (Exception e) {
            return defaultVal;
        }
    }
}

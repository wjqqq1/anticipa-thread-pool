package com.baomihuahua.anticipa.agent.scheduled;

import com.baomihuahua.anticipa.agent.ToolDefinition;
import com.baomihuahua.anticipa.agent.ToolRegistry;
import com.baomihuahua.anticipa.agent.ToolResult;
import com.baomihuahua.anticipa.agent.tool.ToolCategory;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 工具：查询定时任务执行日志。
 * <p>
 * 底层依赖 {@link ScheduledTaskService#getRecentExecutionLogs()}。
 * 让 AI 能查看定时任务的历史执行情况，包括执行结果、分析报告、调整记录等。
 * </p>
 */
public class QueryTaskLogsTool {

    private static final Logger log = LoggerFactory.getLogger(QueryTaskLogsTool.class);

    private final ToolRegistry toolRegistry;
    private final ScheduledTaskService taskService;

    public QueryTaskLogsTool(ToolRegistry toolRegistry, ScheduledTaskService taskService) {
        this.toolRegistry = toolRegistry;
        this.taskService = taskService;
    }

    @PostConstruct
    public void register() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("task_id", Map.of(
                "type", "string",
                "description", "定时任务 ID"
        ));
        properties.put("limit", Map.of(
                "type", "integer",
                "description", "返回条数上限，默认 10"
        ));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"task_id"});

        ToolDefinition tool = ToolDefinition.builder()
                .name("query_task_logs")
                .description("查询指定定时任务的最近执行日志，包括执行时间、结果、分析报告、是否触发自动调整等")
                .category(ToolCategory.SYSTEM)
                .modification(false)
                .needsApproval(false)
                .concurrencySafe(true)
                .parameterSchema(schema)
                .executor(params -> {
                    String taskId = getParamStr(params, "task_id");
                    if (taskId == null || taskId.isEmpty()) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("请指定要查询的任务 ID")
                                .build();
                    }

                    int limit = getParamInt(params, "limit", 10);

                    ScheduledTask task = taskService.get(taskId);
                    if (task == null) {
                        return ToolResult.builder()
                                .success(false)
                                .summary("未找到任务: " + taskId)
                                .build();
                    }

                    try {
                        List<TaskExecutionLog> logs = taskService.getRecentExecutionLogs(taskId, limit);
                        if (logs.isEmpty()) {
                            return ToolResult.success(
                                    "任务 [" + task.getTaskName() + "] 暂无执行日志",
                                    Map.of("taskId", taskId, "logs", List.of(), "count", 0));
                        }

                        List<Map<String, Object>> logList = logs.stream().map(l -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("logId", l.getLogId());
                            m.put("startTime", l.getStartTime() != null ? l.getStartTime().toString() : "");
                            m.put("endTime", l.getEndTime() != null ? l.getEndTime().toString() : "");
                            m.put("durationMs", l.getDurationMs());
                            m.put("result", l.getResult());
                            m.put("action", l.getAction());
                            m.put("adjusted", l.isAdjusted());
                            m.put("adjustDetail", l.getAdjustDetail());
                            m.put("notified", l.isNotified());
                            if (l.getErrorMessage() != null) {
                                m.put("errorMessage", l.getErrorMessage());
                            }
                            return m;
                        }).collect(Collectors.toList());

                        StringBuilder summary = new StringBuilder();
                        summary.append("任务 [").append(task.getTaskName()).append("] 执行日志（最近 ")
                                .append(logs.size()).append(" 条）：\n");
                        for (TaskExecutionLog l : logs) {
                            summary.append("- ").append(l.getStartTime())
                                    .append(" ").append(l.getResult())
                                    .append(" 耗时 ").append(l.getDurationMs()).append("ms");
                            if (l.isAdjusted()) {
                                summary.append(" [已自动调整]");
                            }
                            if (l.getErrorMessage() != null) {
                                summary.append(" 错误: ").append(l.getErrorMessage());
                            }
                            summary.append("\n");
                        }

                        return ToolResult.success(summary.toString().trim(), Map.of(
                                "taskId", taskId,
                                "taskName", task.getTaskName(),
                                "logs", logList,
                                "count", logs.size()));
                    } catch (Exception e) {
                        log.error("[QueryTaskLogsTool] failed", e);
                        return ToolResult.builder()
                                .success(false)
                                .summary("查询执行日志失败：" + e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[QueryTaskLogsTool] registered tool: query_task_logs");
    }

    private String getParamStr(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }

    private int getParamInt(Map<String, Object> params, String key, int defaultVal) {
        Object val = params.get(key);
        if (val == null) return defaultVal;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}

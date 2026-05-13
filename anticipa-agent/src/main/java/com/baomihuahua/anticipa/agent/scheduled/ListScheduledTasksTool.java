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
 * AI 工具：列举所有定时任务。
 * <p>
 * 底层依赖 {@link ScheduledTaskService#list()}。
 * 让 AI 能查看已有的定时巡检任务，是定时任务管理的基础入口。
 * </p>
 */
public class ListScheduledTasksTool {

    private static final Logger log = LoggerFactory.getLogger(ListScheduledTasksTool.class);

    private final ToolRegistry toolRegistry;
    private final ScheduledTaskService taskService;

    public ListScheduledTasksTool(ToolRegistry toolRegistry, ScheduledTaskService taskService) {
        this.toolRegistry = toolRegistry;
        this.taskService = taskService;
    }

    @PostConstruct
    public void register() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());

        ToolDefinition tool = ToolDefinition.builder()
                .name("list_scheduled_tasks")
                .description("查询所有定时任务的列表，包括任务名称、目标线程池、Cron 表达式、状态等信息")
                .category(ToolCategory.SYSTEM)
                .modification(false)
                .needsApproval(false)
                .concurrencySafe(true)
                .parameterSchema(schema)
                .executor(params -> {
                    try {
                        List<ScheduledTask> tasks = taskService.list();
                        if (tasks.isEmpty()) {
                            return ToolResult.success("当前没有任何定时任务", Map.of("tasks", List.of(), "count", 0));
                        }

                        List<Map<String, Object>> taskList = tasks.stream().map(t -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("taskId", t.getTaskId());
                            m.put("taskName", t.getTaskName());
                            m.put("threadPoolId", t.getThreadPoolId());
                            m.put("cronExpression", t.getCronExpression());
                            m.put("action", t.getAction() != null ? t.getAction().name() : "");
                            m.put("status", t.getStatus() != null ? t.getStatus().name() : "");
                            m.put("enabled", t.isEnabled());
                            m.put("source", t.getSource());
                            m.put("nextExecTime", t.getNextExecTime() != null ? t.getNextExecTime().toString() : "");
                            return m;
                        }).collect(Collectors.toList());

                        StringBuilder summary = new StringBuilder();
                        summary.append("共 ").append(tasks.size()).append(" 个定时任务：\n");
                        for (ScheduledTask t : tasks) {
                            summary.append("- ").append(t.getTaskName())
                                    .append(" (").append(t.getStatus() != null ? t.getStatus().name() : "")
                                    .append(") 目标线程池: ").append(t.getThreadPoolId())
                                    .append(" Cron: ").append(t.getCronExpression())
                                    .append("\n");
                        }

                        return ToolResult.success(summary.toString().trim(), Map.of("tasks", taskList, "count", tasks.size()));
                    } catch (Exception e) {
                        log.error("[ListScheduledTasksTool] failed", e);
                        return ToolResult.builder()
                                .success(false)
                                .summary("查询定时任务列表失败：" + e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[ListScheduledTasksTool] registered tool: list_scheduled_tasks");
    }
}

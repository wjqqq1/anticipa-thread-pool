package com.baomihuahua.anticipa.agent.scheduled;

import com.baomihuahua.anticipa.agent.ToolDefinition;
import com.baomihuahua.anticipa.agent.ToolRegistry;
import com.baomihuahua.anticipa.agent.ToolResult;
import com.baomihuahua.anticipa.agent.tool.ToolCategory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 工具：创建定时任务。
 * <p>
 * 向 AI 暴露 create_scheduled_task 工具，使其能够通过自然语言描述创建定时任务。
 * 在 @PostConstruct 阶段自动注册到 ToolRegistry。
 * </p>
 */
public class CreateScheduledTaskTool {

    private static final Logger log = LoggerFactory.getLogger(CreateScheduledTaskTool.class);

    private final ToolRegistry toolRegistry;
    private final ScheduledTaskService taskService;
    private final TaskScheduler taskScheduler;

    public CreateScheduledTaskTool(ToolRegistry toolRegistry, ScheduledTaskService taskService,
                                    TaskScheduler taskScheduler) {
        this.toolRegistry = toolRegistry;
        this.taskService = taskService;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    public void register() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "task_name", Map.of("type", "string", "description", "任务名称，必填且全局唯一（trim 后精确匹配，与已有任务不可重名）"),
                "thread_pool_id", Map.of("type", "string", "description", "目标线程池 ID"),
                "instance_id", Map.of("type", "string", "description", "目标实例地址（必填），格式为 ip:port，例如 192.168.1.10:8080"),
                "cron_expression", Map.of("type", "string", "description", "Cron 表达式，如 0 0 14 * * ?"),
                "action", Map.of(
                        "type", "string",
                        "enum", new String[]{"LOG_ONLY", "NOTIFY_ONLY", "AUTO_ADJUST"},
                        "description", "执行策略（仅此字段控制通知与自动调整）：LOG_ONLY=仅分析；NOTIFY_ONLY=分析并钉钉通知；AUTO_ADJUST=分析、按需自动调整并钉钉通知"
                ),
                "description", Map.of("type", "string", "description", "任务描述，说明需要关注的内容")
        ));
        schema.put("required", new String[]{"task_name", "thread_pool_id", "instance_id", "cron_expression", "action"});

        ToolDefinition tool = ToolDefinition.builder()
                .name("create_scheduled_task")
                .description("创建一个定时任务，定期分析指定线程池的运行情况；是否通知、是否自动调整由 action 枚举决定，无需其它布尔开关")
                .category(ToolCategory.SYSTEM)
                .modification(false)
                .needsApproval(false)
                .concurrencySafe(false)
                .parameterSchema(schema)
                .executor(params -> {
                    try {
                        ScheduledTask task = new ScheduledTask();
                        task.setTaskName(getParam(params, "task_name", "未命名任务"));
                        task.setThreadPoolId(getParam(params, "thread_pool_id", ""));
                        task.setInstanceId(getParam(params, "instance_id", ""));
                        task.setCronExpression(getParam(params, "cron_expression", ""));
                        task.setAction(parseAction(getParam(params, "action", "LOG_ONLY")));
                        task.setDescription(getParam(params, "description", ""));
                        task.setEnabled(true);
                        task.setSource("AI");

                        task = taskService.create(task);
                        taskScheduler.startTask(task);

                        Map<String, Object> resultData = new HashMap<>();
                        resultData.put("taskId", task.getTaskId());
                        resultData.put("taskName", task.getTaskName());
                        resultData.put("cronExpression", task.getCronExpression());

                        return ToolResult.success(
                                "定时任务已创建！任务名称：" + task.getTaskName()
                                        + "，下次执行时间：" + task.getNextExecTime(),
                                resultData);
                    } catch (Exception e) {
                        log.error("[CreateScheduledTaskTool] failed to create task", e);
                        return ToolResult.builder()
                                .success(false)
                                .summary("创建定时任务失败：" + e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[CreateScheduledTaskTool] registered tool: create_scheduled_task");
    }

    private ScheduledTask.TaskAction parseAction(String action) {
        try {
            return ScheduledTask.TaskAction.valueOf(action.toUpperCase());
        } catch (Exception e) {
            return ScheduledTask.TaskAction.LOG_ONLY;
        }
    }

    private String getParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}

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
 * AI 工具：停用定时任务（与控制台「停用」一致，非第三种「暂停」状态）。
 * <p>
 * 底层为 {@link TaskScheduler#pauseTask()}：停止 Cron，状态 {@link ScheduledTask.TaskStatus#DISABLED}。
 * 再次执行请用 {@code resume_scheduled_task}（启用）。
 * </p>
 */
public class PauseScheduledTaskTool {

    private static final Logger log = LoggerFactory.getLogger(PauseScheduledTaskTool.class);

    private final ToolRegistry toolRegistry;
    private final TaskScheduler taskScheduler;
    private final ScheduledTaskService taskService;

    public PauseScheduledTaskTool(ToolRegistry toolRegistry, TaskScheduler taskScheduler,
                                   ScheduledTaskService taskService) {
        this.toolRegistry = toolRegistry;
        this.taskScheduler = taskScheduler;
        this.taskService = taskService;
    }

    @PostConstruct
    public void register() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new HashMap<>();
        props.put("task_id", Map.of("type", "string", "description", "要停用的定时任务 ID（与 task_name 二选一）"));
        props.put("task_name", Map.of("type", "string", "description", "要停用的定时任务名称，trim 后精确匹配（与 task_id 二选一）"));
        schema.put("properties", props);
        schema.put("required", new String[]{});

        ToolDefinition tool = ToolDefinition.builder()
                .name("pause_scheduled_task")
                .description("停用指定定时任务（状态 DISABLED，停止 Cron），与 Dashboard 停用一致。再次运行请用 resume_scheduled_task。参数二选一：task_id 或 task_name")
                .category(ToolCategory.SYSTEM)
                .modification(true)
                .needsApproval(false)
                .concurrencySafe(false)
                .parameterSchema(schema)
                .executor(params -> {
                    try {
                        ScheduledTask task = taskService.resolveTask(
                                getParamStr(params, "task_id"),
                                getParamStr(params, "task_name"));
                        String resolvedId = task.getTaskId();

                        taskScheduler.pauseTask(resolvedId);

                        ScheduledTask after = taskService.get(resolvedId);
                        return ToolResult.success(
                                "定时任务 [" + task.getTaskName() + "] 已停用",
                                Map.of("taskId", resolvedId, "taskName", task.getTaskName(),
                                        "status", after != null && after.getStatus() != null
                                                ? after.getStatus().name() : "DISABLED"));
                    } catch (IllegalArgumentException e) {
                        return ToolResult.builder()
                                .success(false)
                                .summary(e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[PauseScheduledTaskTool] registered tool: pause_scheduled_task");
    }

    private String getParamStr(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }
}

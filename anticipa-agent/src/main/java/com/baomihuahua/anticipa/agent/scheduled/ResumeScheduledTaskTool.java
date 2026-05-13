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
 * AI 工具：启用定时任务（与控制台「启用」一致）。
 * <p>
 * 底层为 {@link TaskScheduler#resumeTask()}：状态 {@link ScheduledTask.TaskStatus#ENABLED} 并挂上 Cron。
 * </p>
 */
public class ResumeScheduledTaskTool {

    private static final Logger log = LoggerFactory.getLogger(ResumeScheduledTaskTool.class);

    private final ToolRegistry toolRegistry;
    private final TaskScheduler taskScheduler;
    private final ScheduledTaskService taskService;

    public ResumeScheduledTaskTool(ToolRegistry toolRegistry, TaskScheduler taskScheduler,
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
        props.put("task_id", Map.of("type", "string", "description", "要启用的定时任务 ID（与 task_name 二选一）"));
        props.put("task_name", Map.of("type", "string", "description", "要启用的定时任务名称，trim 后精确匹配（与 task_id 二选一）"));
        schema.put("properties", props);
        schema.put("required", new String[]{});

        ToolDefinition tool = ToolDefinition.builder()
                .name("resume_scheduled_task")
                .description("启用指定定时任务（状态 ENABLED，按 Cron 调度），与 Dashboard 启用一致。参数二选一：task_id 或 task_name")
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

                        if (task.getStatus() == ScheduledTask.TaskStatus.RUNNING) {
                            return ToolResult.builder()
                                    .success(false)
                                    .summary("任务 [" + task.getTaskName() + "] 正在执行中，请稍后再试启用调度")
                                    .build();
                        }

                        taskScheduler.resumeTask(resolvedId);

                        ScheduledTask after = taskService.get(resolvedId);
                        String statusName = after != null && after.getStatus() != null
                                ? after.getStatus().name() : "ENABLED";
                        return ToolResult.success(
                                "定时任务 [" + task.getTaskName() + "] 已启用并将按 Cron 执行",
                                Map.of("taskId", resolvedId, "taskName", task.getTaskName(), "status", statusName));
                    } catch (IllegalArgumentException e) {
                        return ToolResult.builder()
                                .success(false)
                                .summary(e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[ResumeScheduledTaskTool] registered tool: resume_scheduled_task");
    }

    private String getParamStr(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }
}

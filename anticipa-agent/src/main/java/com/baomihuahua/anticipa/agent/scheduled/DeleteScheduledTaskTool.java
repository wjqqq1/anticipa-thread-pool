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
 * AI 工具：删除定时任务。
 * <p>
 * 底层依赖 {@link ScheduledTaskService#delete()} + {@link TaskScheduler#stopTask()}。
 * 仅允许删除已停用（{@link ScheduledTask.TaskStatus#DISABLED}）的任务；参数支持 task_id 或 task_name。
 * </p>
 */
public class DeleteScheduledTaskTool {

    private static final Logger log = LoggerFactory.getLogger(DeleteScheduledTaskTool.class);

    private final ToolRegistry toolRegistry;
    private final ScheduledTaskService taskService;
    private final TaskScheduler taskScheduler;

    public DeleteScheduledTaskTool(ToolRegistry toolRegistry, ScheduledTaskService taskService,
                                    TaskScheduler taskScheduler) {
        this.toolRegistry = toolRegistry;
        this.taskService = taskService;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    public void register() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new HashMap<>();
        props.put("task_id", Map.of("type", "string", "description", "要删除的定时任务 ID（与 task_name 二选一）"));
        props.put("task_name", Map.of("type", "string", "description", "要删除的定时任务名称，trim 后精确匹配（与 task_id 二选一）"));
        schema.put("properties", props);
        schema.put("required", new String[]{});

        ToolDefinition tool = ToolDefinition.builder()
                .name("delete_scheduled_task")
                .description("删除已停用的定时任务（仅 DISABLED 状态可删）。参数二选一：task_id 或 task_name；若同时提供则以 task_id 为准")
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

                        if (task.getStatus() != ScheduledTask.TaskStatus.DISABLED) {
                            return ToolResult.builder()
                                    .success(false)
                                    .summary("仅停用的任务可删除，当前状态: " + task.getStatus() + "；请先停用后再删除")
                                    .build();
                        }

                        taskScheduler.stopTask(resolvedId);
                        taskService.delete(resolvedId);

                        return ToolResult.success(
                                "定时任务 [" + task.getTaskName() + "] 已删除",
                                Map.of("taskId", resolvedId, "taskName", task.getTaskName()));
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        return ToolResult.builder()
                                .success(false)
                                .summary(e.getMessage())
                                .build();
                    }
                })
                .build();

        toolRegistry.register(tool);
        log.info("[DeleteScheduledTaskTool] registered tool: delete_scheduled_task");
    }

    private String getParamStr(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }
}

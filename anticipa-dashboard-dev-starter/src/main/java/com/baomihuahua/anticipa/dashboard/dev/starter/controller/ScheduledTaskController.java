package com.baomihuahua.anticipa.dashboard.dev.starter.controller;

import com.baomihuahua.anticipa.agent.scheduled.ScheduledTask;
import com.baomihuahua.anticipa.agent.scheduled.ScheduledTaskService;
import com.baomihuahua.anticipa.agent.scheduled.TaskExecutionLog;
import com.baomihuahua.anticipa.agent.scheduled.TaskScheduler;
import com.baomihuahua.anticipa.dashboard.dev.starter.core.Result;
import com.baomihuahua.anticipa.dashboard.dev.starter.core.Results;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定时任务管理 REST 接口。
 * <p>
 * 仅当 anticipa-agent 模块在 classpath 上时启用。
 * </p>
 */
@RestController
@RequestMapping("/api/anticipa-dashboard/scheduled-tasks")
@ConditionalOnClass(name = "com.baomihuahua.anticipa.agent.scheduled.ScheduledTaskService")
public class ScheduledTaskController {

    private static final String SCHEDULER_UNAVAILABLE = "定时任务调度器未就绪（请确认 ThreadPoolQueryService、NotifierDispatcher 等依赖已配置）";

    private final ScheduledTaskService taskService;
    private final TaskScheduler taskScheduler;

    public ScheduledTaskController(ScheduledTaskService taskService,
                                   ObjectProvider<TaskScheduler> taskSchedulerProvider) {
        this.taskService = taskService;
        this.taskScheduler = taskSchedulerProvider.getIfAvailable();
    }

    /**
     * 获取所有定时任务列表
     */
    @GetMapping({"", "/list"})
    public Result<List<ScheduledTask>> list() {
        return Results.success(taskService.list());
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{taskId}")
    public Result<ScheduledTask> get(@PathVariable String taskId) {
        ScheduledTask task = taskService.get(taskId);
        if (task == null) {
            return Results.failure("任务不存在");
        }
        return Results.success(task);
    }

    /**
     * 创建定时任务
     */
    @PostMapping
    public Result<ScheduledTask> create(@RequestBody ScheduledTask task) {
        if (taskScheduler == null) {
            return Results.failure(SCHEDULER_UNAVAILABLE);
        }
        if (task.getInstanceId() == null || task.getInstanceId().isBlank()) {
            return Results.failure("instanceId（实例地址 ip:port）为必填参数");
        }
        task = taskService.create(task);
        taskScheduler.startTask(task);
        return Results.success(task);
    }

    /**
     * 更新任务配置
     */
    @PutMapping("/{taskId}")
    public Result<ScheduledTask> update(@PathVariable String taskId, @RequestBody ScheduledTask task) {
        if (taskScheduler == null) {
            return Results.failure(SCHEDULER_UNAVAILABLE);
        }
        if (task.getInstanceId() == null || task.getInstanceId().isBlank()) {
            return Results.failure("instanceId（实例地址 ip:port）为必填参数");
        }
        task.setTaskId(taskId);
        task = taskService.update(task);
        taskScheduler.startTask(task);
        return Results.success(task);
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{taskId}")
    public Result<Void> delete(@PathVariable String taskId) {
        if (taskScheduler == null) {
            return Results.failure(SCHEDULER_UNAVAILABLE);
        }
        ScheduledTask task = taskService.get(taskId);
        if (task == null) {
            return Results.failure("任务不存在");
        }
        if (task.getStatus() != ScheduledTask.TaskStatus.DISABLED) {
            return Results.failure("仅停用的任务可删除，当前状态: " + task.getStatus() + "；请先停用后再删除");
        }
        taskScheduler.stopTask(taskId);
        taskService.delete(taskId);
        return Results.success();
    }

    /**
     * 启用/停用任务
     */
    @PostMapping("/{taskId}/toggle")
    public Result<ScheduledTask> toggle(@PathVariable String taskId) {
        if (taskScheduler == null) {
            return Results.failure(SCHEDULER_UNAVAILABLE);
        }
        ScheduledTask task = taskService.get(taskId);
        if (task == null) {
            return Results.failure("任务不存在");
        }
        if (task.getStatus() == ScheduledTask.TaskStatus.ENABLED
                || task.getStatus() == ScheduledTask.TaskStatus.RUNNING) {
            taskScheduler.stopTask(taskId);
            taskService.toggle(taskId);
        } else {
            task = taskService.toggle(taskId);
            taskScheduler.startTask(task);
        }
        return Results.success(taskService.get(taskId));
    }

    /**
     * 立即执行一次
     */
    @PostMapping("/{taskId}/execute-now")
    public Result<Void> executeNow(@PathVariable String taskId) {
        if (taskScheduler == null) {
            return Results.failure(SCHEDULER_UNAVAILABLE);
        }
        ScheduledTask task = taskService.get(taskId);
        if (task == null) {
            return Results.failure("任务不存在");
        }
        taskScheduler.executeNow(taskId);
        return Results.success();
    }

    /**
     * 获取执行历史
     */
    @GetMapping("/{taskId}/executions")
    public Result<List<TaskExecutionLog>> getExecutions(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "20") int limit) {
        return Results.success(taskService.getRecentExecutionLogs(taskId, limit));
    }
}

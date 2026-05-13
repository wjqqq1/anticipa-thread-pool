package com.baomihuahua.anticipa.dashboard.dev.server.controller;

import com.baomihuahua.anticipa.agent.scheduled.ScheduledTask;
import com.baomihuahua.anticipa.agent.scheduled.ScheduledTaskService;
import com.baomihuahua.anticipa.agent.scheduled.TaskExecutionLog;
import com.baomihuahua.anticipa.agent.scheduled.TaskScheduler;
import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定时任务管理 REST 接口。
 * <p>
 * 通过 DashboardScheduledTaskAutoConfiguration 自动配置注册，
 * 仅在 anticipa.agent.enabled=true 时启用。
 * </p>
 */
@RestController
@RequestMapping("/api/anticipa-dashboard/scheduled-tasks")
public class ScheduledTaskController {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskController.class);

    private static final String SCHEDULER_UNAVAILABLE = "定时任务调度器未就绪（请确认 ThreadPoolQueryService、NotifierDispatcher 等依赖已配置）";

    private final ScheduledTaskService taskService;
    private final TaskScheduler taskScheduler;

    public ScheduledTaskController(ScheduledTaskService taskService,
                                   ObjectProvider<TaskScheduler> taskSchedulerProvider) {
        this.taskService = taskService;
        this.taskScheduler = taskSchedulerProvider.getIfAvailable();
        log.info("[ScheduledTaskController] initialized, ScheduledTaskService={}, TaskScheduler present={}",
                taskService.getClass().getName(), this.taskScheduler != null);
    }

    @GetMapping({"", "/list"})
    public Result<List<ScheduledTask>> list() {
        List<ScheduledTask> tasks = taskService.list();
        log.info("[ScheduledTaskController] list() called, returned {} tasks", tasks.size());
        return Result.success(tasks);
    }

    @GetMapping("/{taskId}")
    public Result<ScheduledTask> get(@PathVariable String taskId) {
        log.info("[ScheduledTaskController] get() called, taskId={}", taskId);
        ScheduledTask task = taskService.get(taskId);
        if (task == null) {
            return Result.failure(-1, "任务不存在");
        }
        return Result.success(task);
    }

    @PostMapping
    public Result<ScheduledTask> create(@RequestBody ScheduledTask task) {
        log.info("[ScheduledTaskController] create() called, taskName={}, threadPoolId={}", task.getTaskName(), task.getThreadPoolId());
        if (taskScheduler == null) {
            return Result.failure(-1, SCHEDULER_UNAVAILABLE);
        }
        if (task.getInstanceId() == null || task.getInstanceId().isBlank()) {
            return Result.failure(-1, "instanceId（实例地址 ip:port）为必填参数");
        }
        task = taskService.create(task);
        taskScheduler.startTask(task);
        return Result.success(task);
    }

    @PutMapping("/{taskId}")
    public Result<ScheduledTask> update(@PathVariable String taskId, @RequestBody ScheduledTask task) {
        log.info("[ScheduledTaskController] update() called, taskId={}", taskId);
        if (taskScheduler == null) {
            return Result.failure(-1, SCHEDULER_UNAVAILABLE);
        }
        if (task.getInstanceId() == null || task.getInstanceId().isBlank()) {
            return Result.failure(-1, "instanceId（实例地址 ip:port）为必填参数");
        }
        task.setTaskId(taskId);
        task = taskService.update(task);
        taskScheduler.startTask(task);
        return Result.success(task);
    }

    @DeleteMapping("/{taskId}")
    public Result<Void> delete(@PathVariable String taskId) {
        log.info("[ScheduledTaskController] delete() called, taskId={}", taskId);
        if (taskScheduler == null) {
            return Result.failure(-1, SCHEDULER_UNAVAILABLE);
        }
        ScheduledTask task = taskService.get(taskId);
        if (task == null) {
            return Result.failure(-1, "任务不存在");
        }
        if (task.getStatus() != ScheduledTask.TaskStatus.DISABLED) {
            return Result.failure(-1, "仅停用的任务可删除，当前状态: " + task.getStatus() + "；请先停用后再删除");
        }
        taskScheduler.stopTask(taskId);
        taskService.delete(taskId);
        return Result.success();
    }

    @PostMapping("/{taskId}/toggle")
    public Result<ScheduledTask> toggle(@PathVariable String taskId) {
        log.info("[ScheduledTaskController] toggle() called, taskId={}", taskId);
        if (taskScheduler == null) {
            return Result.failure(-1, SCHEDULER_UNAVAILABLE);
        }
        ScheduledTask task = taskService.get(taskId);
        if (task == null) {
            return Result.failure(-1, "任务不存在");
        }
        if (task.getStatus() == ScheduledTask.TaskStatus.ENABLED
                || task.getStatus() == ScheduledTask.TaskStatus.RUNNING) {
            taskScheduler.stopTask(taskId);
            taskService.toggle(taskId);
        } else {
            task = taskService.toggle(taskId);
            taskScheduler.startTask(task);
        }
        return Result.success(taskService.get(taskId));
    }

    @PostMapping("/{taskId}/execute-now")
    public Result<Void> executeNow(@PathVariable String taskId) {
        log.info("[ScheduledTaskController] executeNow() called, taskId={}", taskId);
        if (taskScheduler == null) {
            return Result.failure(-1, SCHEDULER_UNAVAILABLE);
        }
        ScheduledTask task = taskService.get(taskId);
        if (task == null) {
            return Result.failure(-1, "任务不存在");
        }
        taskScheduler.executeNow(taskId);
        return Result.success();
    }

    @GetMapping("/{taskId}/executions")
    public Result<List<TaskExecutionLog>> getExecutions(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("[ScheduledTaskController] getExecutions() called, taskId={}, limit={}", taskId, limit);
        return Result.success(taskService.getRecentExecutionLogs(taskId, limit));
    }
}

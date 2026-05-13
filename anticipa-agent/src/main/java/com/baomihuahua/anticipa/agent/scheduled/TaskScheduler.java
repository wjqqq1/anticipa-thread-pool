package com.baomihuahua.anticipa.agent.scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务调度引擎。
 * <p>
 * 基于 Spring TaskScheduler 的 CronTrigger 实现，
 * 管理所有定时任务的启动与停止；方法名 pause/resume 与 AI 工具对齐，语义为停用/启用（仅两种业务状态）。
 * 应用启动时自动加载已启用的任务。
 * </p>
 */
public class TaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskScheduler.class);

    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final TaskExecutionService executionService;
    private final ScheduledTaskService taskService;

    public TaskScheduler(TaskExecutionService executionService, ScheduledTaskService taskService) {
        this.executionService = executionService;
        this.taskService = taskService;
        this.taskScheduler = new ThreadPoolTaskScheduler();
        this.taskScheduler.setPoolSize(4);
        this.taskScheduler.setThreadNamePrefix("scheduled-task-");
        this.taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        this.taskScheduler.setAwaitTerminationSeconds(30);
        this.taskScheduler.initialize();
    }

    /**
     * 初始化：加载所有已启用的任务，清理过期执行日志。
     */
    @PostConstruct
    public void init() {
        // 清理过期的执行日志文件
        taskService.cleanExpiredLogs();

        List<ScheduledTask> enabledTasks = taskService.listEnabled();
        for (ScheduledTask task : enabledTasks) {
            try {
                startTask(task);
            } catch (Exception e) {
                log.error("[TaskScheduler] failed to start task: {} ({})", task.getTaskId(), task.getTaskName(), e);
            }
        }
        log.info("[TaskScheduler] initialized with {} enabled tasks", enabledTasks.size());
    }

    /**
     * 启动（或重启）一个定时任务。
     * <p>
     * 仅负责调度逻辑（创建/重启 ScheduledFuture），不修改任务状态。
     * 状态管理应由调用方（Controller toggle / create / update）负责。
     * </p>
     */
    public void startTask(ScheduledTask task) {
        // 先停止已有的调度
        stopTask(task.getTaskId());

        CronTrigger trigger = new CronTrigger(task.getCronExpression());
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeTask(task), trigger);
        scheduledTasks.put(task.getTaskId(), future);

        // 更新下次执行时间（不覆盖 status）
        updateNextExecTime(task, trigger);

        log.info("[TaskScheduler] started task: {} ({}) cron={}", task.getTaskId(), task.getTaskName(), task.getCronExpression());
    }

    /**
     * 停止一个定时任务。
     * <p>
     * 仅取消调度，不修改任务状态。状态管理由调用方负责。
     * </p>
     */
    public void stopTask(String taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
        }
    }

    /**
     * 停用任务：取消调度并将状态置为 {@link ScheduledTask.TaskStatus#DISABLED}（与控制台「停用」一致）。
     */
    public void pauseTask(String taskId) {
        stopTask(taskId);
        taskService.disable(taskId);
        log.info("[TaskScheduler] disabled task: {}", taskId);
    }

    /**
     * 启用任务：状态置为 {@link ScheduledTask.TaskStatus#ENABLED} 并重新挂上 Cron（与控制台「启用」一致）。
     */
    public void resumeTask(String taskId) {
        ScheduledTask task = taskService.get(taskId);
        if (task == null) {
            return;
        }
        if (task.getStatus() == ScheduledTask.TaskStatus.RUNNING) {
            return;
        }
        taskService.enable(taskId);
        task = taskService.get(taskId);
        if (task != null && task.getStatus() == ScheduledTask.TaskStatus.ENABLED) {
            startTask(task);
        }
    }

    /**
     * 立即执行一次任务（不影响下次调度）。
     */
    public void executeNow(String taskId) {
        ScheduledTask task = taskService.get(taskId);
        if (task == null) {
            return;
        }
        taskScheduler.submit(() -> executionService.execute(task));
    }

    /**
     * 优雅关闭。
     */
    @PreDestroy
    public void destroy() {
        log.info("[TaskScheduler] shutting down...");
        for (String taskId : scheduledTasks.keySet()) {
            stopTask(taskId);
        }
        taskScheduler.destroy();
    }

    private void executeTask(ScheduledTask task) {
        log.info("[TaskScheduler] >>> executing task: id={}, name={}, threadPoolId={}, action={}",
                task.getTaskId(), task.getTaskName(), task.getThreadPoolId(), task.getAction());
        executionService.execute(task);
        // 更新下次执行时间（仅当任务仍为启用状态时）
        ScheduledTask current = taskService.get(task.getTaskId());
        if (current != null && current.getStatus() != ScheduledTask.TaskStatus.DISABLED) {
            CronTrigger trigger = new CronTrigger(current.getCronExpression());
            Date nextExec = trigger.nextExecutionTime(
                    new SimpleTriggerContext());
            if (nextExec != null) {
                current.setNextExecTime(LocalDateTime.ofInstant(
                        nextExec.toInstant(), ZoneId.systemDefault()));
                taskService.update(current);
                log.info("[TaskScheduler] <<< task {} completed, nextExecTime={}", task.getTaskId(), current.getNextExecTime());
            }
        } else if (current != null) {
            log.info("[TaskScheduler] <<< task {} completed but task is disabled, skip updating nextExecTime", task.getTaskId());
            // 仍需保存 lastExecTime（由 execute() 设置）
            taskService.update(current);
        } else {
            log.warn("[TaskScheduler] task {} not found after execution, may have been deleted", task.getTaskId());
        }
    }

    /**
     * 更新任务的下次执行时间，不覆盖 status 字段。
     */
    private void updateNextExecTime(ScheduledTask task, CronTrigger trigger) {
        Date nextExec = trigger.nextExecutionTime(new SimpleTriggerContext());
        if (nextExec != null) {
            task.setNextExecTime(LocalDateTime.ofInstant(
                    nextExec.toInstant(), ZoneId.systemDefault()));
        }
        taskService.update(task);
    }
}

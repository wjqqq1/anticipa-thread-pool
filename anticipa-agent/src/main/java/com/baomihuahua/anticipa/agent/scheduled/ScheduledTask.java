package com.baomihuahua.anticipa.agent.scheduled;

import java.time.LocalDateTime;

/**
 * 定时任务模型。
 * <p>
 * 定义一次定时分析的完整配置：目标线程池、调度表达式、执行策略等。
 * 持久化存储由 ScheduledTaskService 管理。
 * </p>
 */
public class ScheduledTask {

    private String taskId;
    private String taskName;
    private String description;

    /** 目标线程池 ID */
    private String threadPoolId;

    /** 目标实例 ID（必填，格式 ip:port） */
    private String instanceId;

    /** Cron 表达式，如 "0 0 14 * * ?"（每天 14 点） */
    private String cronExpression;

    /** 是否启用 */
    private boolean enabled;

    /** 操作类型 */
    private TaskAction action;

    /** 执行完成是否通知 */
    private boolean notifyOnComplete;

    /** 通知 Webhook（为空则使用系统默认钉钉） */
    private String notifyWebhookUrl;

    /** 是否自动调整参数 */
    private boolean autoAdjust;

    /** 状态 */
    private TaskStatus status;

    /** 上次执行时间 */
    private LocalDateTime lastExecTime;

    /** 下次计划执行时间 */
    private LocalDateTime nextExecTime;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 创建者来源："USER" / "AI" */
    private String source;

    public ScheduledTask() {
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getThreadPoolId() { return threadPoolId; }
    public void setThreadPoolId(String threadPoolId) { this.threadPoolId = threadPoolId; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public TaskAction getAction() { return action; }
    public void setAction(TaskAction action) { this.action = action; }
    public boolean isNotifyOnComplete() { return notifyOnComplete; }
    public void setNotifyOnComplete(boolean notifyOnComplete) { this.notifyOnComplete = notifyOnComplete; }
    public String getNotifyWebhookUrl() { return notifyWebhookUrl; }
    public void setNotifyWebhookUrl(String notifyWebhookUrl) { this.notifyWebhookUrl = notifyWebhookUrl; }
    public boolean isAutoAdjust() { return autoAdjust; }
    public void setAutoAdjust(boolean autoAdjust) { this.autoAdjust = autoAdjust; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public LocalDateTime getLastExecTime() { return lastExecTime; }
    public void setLastExecTime(LocalDateTime lastExecTime) { this.lastExecTime = lastExecTime; }
    public LocalDateTime getNextExecTime() { return nextExecTime; }
    public void setNextExecTime(LocalDateTime nextExecTime) { this.nextExecTime = nextExecTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    /** 操作类型 */
    public enum TaskAction {
        /** 仅采集日志 + 分析 */
        LOG_ONLY,
        /** 采集 + 分析 + 发钉钉通知 */
        NOTIFY_ONLY,
        /** 采集 + 分析 + 直接调整 + 发钉钉通知 */
        AUTO_ADJUST
    }

    /**
     * 任务状态。
     * <p>
     * 业务上只区分「启用」与「停用」：{@link #ENABLED} / {@link #DISABLED}。
     * {@link #RUNNING} 为单次触发执行中的瞬时态；{@link #FAILED} 表示末次执行失败。
     * {@link #PAUSED} 为历史兼容值，加载持久化时会迁移为 {@link #DISABLED}，不应再写入。
     * </p>
     */
    public enum TaskStatus {
        ENABLED,
        DISABLED,
        RUNNING,
        FAILED,
        /** @deprecated 仅旧数据兼容，等同于停用，见 {@link ScheduledTaskService} 载入迁移 */
        @Deprecated
        PAUSED
    }
}

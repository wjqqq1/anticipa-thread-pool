package com.baomihuahua.anticipa.agent.scheduled;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志。
 * <p>
 * 每次定时任务触发和执行完毕后，记录执行结果、分析报告、调整记录等。
 * </p>
 */
public class TaskExecutionLog {

    private String logId;
    private String taskId;
    private String taskName;
    private String threadPoolId;

    /** 执行开始时间 */
    private LocalDateTime startTime;

    /** 执行结束时间 */
    private LocalDateTime endTime;

    /** 执行耗时（毫秒） */
    private long durationMs;

    /** 执行结果：SUCCESS / FAILED */
    private String result;

    /** 错误信息（失败时填写） */
    private String errorMessage;

    /** AI 分析报告全文 */
    private String analysisReport;

    /** 执行策略 */
    private String action;

    /** 是否触发了自动调整 */
    private boolean adjusted;

    /** 调整详情（JSON 格式） */
    private String adjustDetail;

    /** 是否发送了通知 */
    private boolean notified;

    /** 通知结果 */
    private String notifyResult;

    private LocalDateTime createdAt;

    public TaskExecutionLog() {
    }

    public String getLogId() { return logId; }
    public void setLogId(String logId) { this.logId = logId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getThreadPoolId() { return threadPoolId; }
    public void setThreadPoolId(String threadPoolId) { this.threadPoolId = threadPoolId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getAnalysisReport() { return analysisReport; }
    public void setAnalysisReport(String analysisReport) { this.analysisReport = analysisReport; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public boolean isAdjusted() { return adjusted; }
    public void setAdjusted(boolean adjusted) { this.adjusted = adjusted; }
    public String getAdjustDetail() { return adjustDetail; }
    public void setAdjustDetail(String adjustDetail) { this.adjustDetail = adjustDetail; }
    public boolean isNotified() { return notified; }
    public void setNotified(boolean notified) { this.notified = notified; }
    public String getNotifyResult() { return notifyResult; }
    public void setNotifyResult(String notifyResult) { this.notifyResult = notifyResult; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

package com.baomihuahua.anticipa.agent;

import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorProperties;
import com.baomihuahua.anticipa.core.monitor.ThreadPoolLogRecord;

import java.util.List;

/**
 * 静默执行请求。
 * <p>
 * 定时任务触发时，由 TaskExecutionService 构建并发起。
 * 无需用户交互，Agent 直接分析并返回结构化报告。
 * <p>
 * 支持两种数据来源：
 * <ul>
 *   <li>本地模式：通过 recentLogs + config 传入结构化对象</li>
 *   <li>远程模式：通过 logSummaryText + configText 传入预渲染文本</li>
 * </ul>
 * 构建系统 Prompt 时优先使用文本字段（非空时），回退到结构化对象。
 */
public class SilentRequest {

    private String threadPoolId;
    private String instanceId;

    /** 本地模式：结构化日志记录 */
    private List<ThreadPoolLogRecord> recentLogs;

    /** 本地模式：结构化配置对象 */
    private ThreadPoolExecutorProperties config;

    /** 远程模式：预渲染的日志摘要文本（优先于 recentLogs） */
    private String logSummaryText;

    /** 远程模式：预渲染的配置描述文本（优先于 config） */
    private String configText;

    private String businessDescription;
    private boolean autoAdjust;

    public SilentRequest() {
    }

    public String getThreadPoolId() { return threadPoolId; }
    public void setThreadPoolId(String threadPoolId) { this.threadPoolId = threadPoolId; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public List<ThreadPoolLogRecord> getRecentLogs() { return recentLogs; }
    public void setRecentLogs(List<ThreadPoolLogRecord> recentLogs) { this.recentLogs = recentLogs; }
    public ThreadPoolExecutorProperties getConfig() { return config; }
    public void setConfig(ThreadPoolExecutorProperties config) { this.config = config; }
    public String getLogSummaryText() { return logSummaryText; }
    public void setLogSummaryText(String logSummaryText) { this.logSummaryText = logSummaryText; }
    public String getConfigText() { return configText; }
    public void setConfigText(String configText) { this.configText = configText; }
    public String getBusinessDescription() { return businessDescription; }
    public void setBusinessDescription(String businessDescription) { this.businessDescription = businessDescription; }
    public boolean isAutoAdjust() { return autoAdjust; }
    public void setAutoAdjust(boolean autoAdjust) { this.autoAdjust = autoAdjust; }
}

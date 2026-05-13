package com.baomihuahua.anticipa.agent;

import java.util.List;
import java.util.Map;

/**
 * Agent 静默分析报告。
 * <p>
 * 定时任务场景下，AI 分析完成后返回的结构化结果。
 * 包含运行概览、分析结论、调整建议和执行结果。
 * </p>
 */
public class AgentReport {

    /** 分析结论摘要 */
    private String summary;

    /** 详细分析文本 */
    private String analysis;

    /** 运行概览 */
    private OperationOverview overview;

    /** 是否建议调整 */
    private boolean adjustmentRecommended;

    /** 建议的调整参数 */
    private Map<String, Object> suggestedAdjustments;

    /** 调整理由 */
    private String adjustReason;

    /** 是否已执行调整 */
    private boolean adjustmentApplied;

    /** 调整执行结果 */
    private String adjustmentResult;

    /** 整体健康状态：HEALTHY / WARNING / CRITICAL */
    private String healthStatus;

    public AgentReport() {
    }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
    public OperationOverview getOverview() { return overview; }
    public void setOverview(OperationOverview overview) { this.overview = overview; }
    public boolean isAdjustmentRecommended() { return adjustmentRecommended; }
    public void setAdjustmentRecommended(boolean adjustmentRecommended) { this.adjustmentRecommended = adjustmentRecommended; }
    public Map<String, Object> getSuggestedAdjustments() { return suggestedAdjustments; }
    public void setSuggestedAdjustments(Map<String, Object> suggestedAdjustments) { this.suggestedAdjustments = suggestedAdjustments; }
    public String getAdjustReason() { return adjustReason; }
    public void setAdjustReason(String adjustReason) { this.adjustReason = adjustReason; }
    public boolean isAdjustmentApplied() { return adjustmentApplied; }
    public void setAdjustmentApplied(boolean adjustmentApplied) { this.adjustmentApplied = adjustmentApplied; }
    public String getAdjustmentResult() { return adjustmentResult; }
    public void setAdjustmentResult(String adjustmentResult) { this.adjustmentResult = adjustmentResult; }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }

    /** 运行概览 */
    public static class OperationOverview {
        private int avgActiveCount;
        private int maxActiveCount;
        private int avgPoolSize;
        private int maxPoolSize;
        private int avgQueueUsagePercent;
        private int maxQueueUsagePercent;
        private long totalRejectCount;

        public int getAvgActiveCount() { return avgActiveCount; }
        public void setAvgActiveCount(int avgActiveCount) { this.avgActiveCount = avgActiveCount; }
        public int getMaxActiveCount() { return maxActiveCount; }
        public void setMaxActiveCount(int maxActiveCount) { this.maxActiveCount = maxActiveCount; }
        public int getAvgPoolSize() { return avgPoolSize; }
        public void setAvgPoolSize(int avgPoolSize) { this.avgPoolSize = avgPoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getAvgQueueUsagePercent() { return avgQueueUsagePercent; }
        public void setAvgQueueUsagePercent(int avgQueueUsagePercent) { this.avgQueueUsagePercent = avgQueueUsagePercent; }
        public int getMaxQueueUsagePercent() { return maxQueueUsagePercent; }
        public void setMaxQueueUsagePercent(int maxQueueUsagePercent) { this.maxQueueUsagePercent = maxQueueUsagePercent; }
        public long getTotalRejectCount() { return totalRejectCount; }
        public void setTotalRejectCount(long totalRejectCount) { this.totalRejectCount = totalRejectCount; }
    }
}

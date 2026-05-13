package com.baomihuahua.anticipa.core.monitor;

/**
 * 线程池运行日志记录。
 * <p>
 * 由 ThreadPoolMonitor 定期采集并持久化到 JSON Lines 文件中。
 * 字段名使用缩写以减小存储体积。
 * </p>
 */
public class ThreadPoolLogRecord {

    /** 线程池 ID */
    private String p;
    /** 采集时间戳（毫秒） */
    private long t;
    /** 核心线程数 */
    private int c;
    /** 最大线程数 */
    private int m;
    /** 当前线程数 */
    private int s;
    /** 活跃线程数 */
    private int a;
    /** 历史最大线程数 */
    private int l;
    /** 已完成任务数 */
    private long ct;
    /** 当前队列大小 */
    private int qs;
    /** 队列容量 */
    private int qc;
    /** 队列剩余容量 */
    private int qr;
    /** 拒绝计数 */
    private int rc;
    /** 拒绝策略名称 */
    private String rh;
    /** 实例标识（ip:port） */
    private String i;

    public ThreadPoolLogRecord() {
    }

    // --- builder-style setters ---

    public ThreadPoolLogRecord threadPoolId(String threadPoolId) { this.p = threadPoolId; return this; }
    public ThreadPoolLogRecord timestamp(long timestamp) { this.t = timestamp; return this; }
    public ThreadPoolLogRecord corePoolSize(int corePoolSize) { this.c = corePoolSize; return this; }
    public ThreadPoolLogRecord maximumPoolSize(int maximumPoolSize) { this.m = maximumPoolSize; return this; }
    public ThreadPoolLogRecord poolSize(int poolSize) { this.s = poolSize; return this; }
    public ThreadPoolLogRecord activeCount(int activeCount) { this.a = activeCount; return this; }
    public ThreadPoolLogRecord largestPoolSize(int largestPoolSize) { this.l = largestPoolSize; return this; }
    public ThreadPoolLogRecord completedTaskCount(long completedTaskCount) { this.ct = completedTaskCount; return this; }
    public ThreadPoolLogRecord queueSize(int queueSize) { this.qs = queueSize; return this; }
    public ThreadPoolLogRecord queueCapacity(int queueCapacity) { this.qc = queueCapacity; return this; }
    public ThreadPoolLogRecord queueRemainingCapacity(int queueRemainingCapacity) { this.qr = queueRemainingCapacity; return this; }
    public ThreadPoolLogRecord rejectCount(int rejectCount) { this.rc = rejectCount; return this; }
    public ThreadPoolLogRecord rejectedHandler(String rejectedHandler) { this.rh = rejectedHandler; return this; }
    public ThreadPoolLogRecord instanceId(String instanceId) { this.i = instanceId; return this; }

    // --- getters ---

    public String getThreadPoolId() { return p; }
    public long getTimestamp() { return t; }
    public int getCorePoolSize() { return c; }
    public int getMaximumPoolSize() { return m; }
    public int getPoolSize() { return s; }
    public int getActiveCount() { return a; }
    public int getLargestPoolSize() { return l; }
    public long getCompletedTaskCount() { return ct; }
    public int getQueueSize() { return qs; }
    public int getQueueCapacity() { return qc; }
    public int getQueueRemainingCapacity() { return qr; }
    public int getRejectCount() { return rc; }
    public String getRejectedHandler() { return rh; }
    public String getInstanceId() { return i; }

    public void setThreadPoolId(String threadPoolId) { this.p = threadPoolId; }
    public void setTimestamp(long timestamp) { this.t = timestamp; }
    public void setCorePoolSize(int corePoolSize) { this.c = corePoolSize; }
    public void setMaximumPoolSize(int maximumPoolSize) { this.m = maximumPoolSize; }
    public void setPoolSize(int poolSize) { this.s = poolSize; }
    public void setActiveCount(int activeCount) { this.a = activeCount; }
    public void setLargestPoolSize(int largestPoolSize) { this.l = largestPoolSize; }
    public void setCompletedTaskCount(long completedTaskCount) { this.ct = completedTaskCount; }
    public void setQueueSize(int queueSize) { this.qs = queueSize; }
    public void setQueueCapacity(int queueCapacity) { this.qc = queueCapacity; }
    public void setQueueRemainingCapacity(int queueRemainingCapacity) { this.qr = queueRemainingCapacity; }
    public void setRejectCount(int rejectCount) { this.rc = rejectCount; }
    public void setRejectedHandler(String rejectedHandler) { this.rh = rejectedHandler; }
    public void setInstanceId(String instanceId) { this.i = instanceId; }
}

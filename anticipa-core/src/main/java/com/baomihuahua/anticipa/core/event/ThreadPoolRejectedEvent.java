package com.baomihuahua.anticipa.core.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 线程池任务拒绝事件。
 * <p>
 * 当线程池触发拒绝策略时发布此事件，供 AI 分析模块或通知模块消费。
 * 携带拒绝时刻的线程池运行时快照，避免消费方再次采集。
 * </p>
 */
public class ThreadPoolRejectedEvent extends ApplicationEvent {

    /** 线程池唯一标识 */
    private final String threadPoolId;

    /** 被拒绝的任务类名 */
    private final String taskClassName;

    /** 当前活跃线程数 */
    private final int activeCount;

    /** 当前线程池大小 */
    private final int poolSize;

    /** 核心线程数 */
    private final int corePoolSize;

    /** 最大线程数 */
    private final int maximumPoolSize;

    /** 当前队列大小 */
    private final int queueSize;

    /** 队列容量 */
    private final int queueCapacity;

    /** 已完成任务数 */
    private final long completedTaskCount;

    /** 总任务数 */
    private final long taskCount;

    /** 拒绝发生时间戳（毫秒） */
    private final long timestamp;

    /**
     * 创建拒绝事件。
     *
     * @param source            事件源（通常是 ThreadPoolExecutor 实例）
     * @param threadPoolId      线程池唯一标识
     * @param taskClassName     被拒绝的任务类名
     * @param activeCount       当前活跃线程数
     * @param poolSize          当前线程池大小
     * @param corePoolSize      核心线程数
     * @param maximumPoolSize   最大线程数
     * @param queueSize         当前队列大小
     * @param queueCapacity     队列容量
     * @param completedTaskCount 已完成任务数
     * @param taskCount         总任务数
     */
    public ThreadPoolRejectedEvent(Object source,
                                   String threadPoolId,
                                   String taskClassName,
                                   int activeCount,
                                   int poolSize,
                                   int corePoolSize,
                                   int maximumPoolSize,
                                   int queueSize,
                                   int queueCapacity,
                                   long completedTaskCount,
                                   long taskCount) {
        super(source);
        this.threadPoolId = threadPoolId;
        this.taskClassName = taskClassName;
        this.activeCount = activeCount;
        this.poolSize = poolSize;
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.queueSize = queueSize;
        this.queueCapacity = queueCapacity;
        this.completedTaskCount = completedTaskCount;
        this.taskCount = taskCount;
        this.timestamp = System.currentTimeMillis();
    }

    // ========== getters ==========

    public String getThreadPoolId() { return threadPoolId; }
    public String getTaskClassName() { return taskClassName; }
    public int getActiveCount() { return activeCount; }
    public int getPoolSize() { return poolSize; }
    public int getCorePoolSize() { return corePoolSize; }
    public int getMaximumPoolSize() { return maximumPoolSize; }
    public int getQueueSize() { return queueSize; }
    public int getQueueCapacity() { return queueCapacity; }
    public int getQueueRemainingCapacity() { return Math.max(0, queueCapacity - queueSize); }
    public long getCompletedTaskCount() { return completedTaskCount; }
    public long getTaskCount() { return taskCount; }
    public long getEventTimestamp() { return timestamp; }

    /** 队列使用率百分比 */
    public int getQueueUsagePercent() {
        return queueCapacity > 0 ? (int) (queueSize * 100.0 / queueCapacity) : 0;
    }

    /** 格式化的拒绝时间 */
    public String getFormattedTime() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        return fmt.format(Instant.ofEpochMilli(timestamp));
    }

    @Override
    public String toString() {
        return "ThreadPoolRejectedEvent{" +
                "threadPoolId='" + threadPoolId + '\'' +
                ", taskClassName='" + taskClassName + '\'' +
                ", activeCount=" + activeCount +
                ", poolSize=" + poolSize +
                ", corePoolSize=" + corePoolSize +
                ", maximumPoolSize=" + maximumPoolSize +
                ", queueUsage=" + getQueueUsagePercent() + "%" +
                ", completedTaskCount=" + completedTaskCount +
                ", timestamp=" + getFormattedTime() +
                '}';
    }
}

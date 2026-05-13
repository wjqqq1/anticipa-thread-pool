package com.baomihuahua.anticipa.core.monitor;

import com.baomihuahua.anticipa.core.config.ThreadPoolLogConfig;
import com.baomihuahua.anticipa.core.executor.AnticipaRegistry;
import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorHolder;
import com.baomihuahua.anticipa.core.executor.support.ResizableCapacityLinkedBlockingQueue;
import com.baomihuahua.anticipa.core.toolkit.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池运行时数据采集、监控与上报。
 * <p>
 * 按 {@link ThreadPoolLogConfig#getRecordIntervalSeconds()} 配置的间隔采集线程池快照，
 * 输出日志并可选持久化到日志文件。
 * </p>
 */
@Slf4j
public class ThreadPoolMonitor {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            1,
            ThreadFactoryBuilder.builder()
                    .namePrefix("scheduler_thread-pool_monitor")
                    .build()
    );

    private final Map<String, DeltaWrapper> deltaMap = new ConcurrentHashMap<>();
    private final ThreadPoolLogStore logStore;
    private final ThreadPoolLogConfig logConfig;
    private final String instanceId;

    /** 最近一次采集的各线程池拒绝计数快照，用于计算增量 */
    private final Map<String, Long> lastRejectCountMap = new ConcurrentHashMap<>();

    public ThreadPoolMonitor(ThreadPoolLogStore logStore, ThreadPoolLogConfig logConfig, String instanceId) {
        this.logStore = logStore;
        this.logConfig = logConfig;
        this.instanceId = instanceId;
    }

    /**
     * 兼容旧构造函数（instanceId 为空）。
     */
    public ThreadPoolMonitor(ThreadPoolLogStore logStore, ThreadPoolLogConfig logConfig) {
        this(logStore, logConfig, null);
    }

    /**
     * 启动监控任务
     */
    public void start() {
        long intervalSeconds = logConfig.getRecordIntervalSeconds();
        log.info("[ThreadPoolMonitor] starting with interval={}s, logEnabled={}", intervalSeconds, logConfig.isEnabled());
        scheduler.scheduleWithFixedDelay(this::collect, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * 停止监控任务
     */
    public void stop() {
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        logStore.flushAll();
    }

    /**
     * 收集线程池运行时信息
     */
    private void collect() {
        Collection<ThreadPoolExecutorHolder> holders = AnticipaRegistry.getAllHolders();

        for (ThreadPoolExecutorHolder holder : holders) {
            ThreadPoolExecutor executor = holder.getExecutor();
            String threadPoolId = holder.getThreadPoolId();

            ThreadPoolRuntimeInfo runtimeInfo = ThreadPoolRuntimeInfo.builder()
                    .threadPoolId(threadPoolId)
                    .applicationName("anticipa")
                    .corePoolSize(executor.getCorePoolSize())
                    .maximumPoolSize(executor.getMaximumPoolSize())
                    .poolSize(executor.getPoolSize())
                    .activeCount(executor.getActiveCount())
                    .largestPoolSize(executor.getLargestPoolSize())
                    .completedTaskCount(executor.getCompletedTaskCount())
                    .queueType(executor.getQueue().getClass().getSimpleName())
                    .queueSize(executor.getQueue().size())
                    .queueRemainingCapacity(executor.getQueue().remainingCapacity())
                    .queueCapacity(executor.getQueue().size() + executor.getQueue().remainingCapacity())
                    .rejectedExecutionHandler(executor.getRejectedExecutionHandler().toString())
                    .build();

            if (executor.getQueue() instanceof ResizableCapacityLinkedBlockingQueue) {
                ResizableCapacityLinkedBlockingQueue<?> queue = (ResizableCapacityLinkedBlockingQueue<?>) executor.getQueue();
                runtimeInfo.setQueueCapacity(queue.getCapacity());
            }

            // 打印或者通过某种方式输出
            log.info("ThreadPoolMonitor: {}", runtimeInfo);

            // 持久化到日志文件
            long rejectCount = executor.getCompletedTaskCount() > 0
                    ? (long) (executor.getTaskCount() - executor.getCompletedTaskCount())
                    : 0;
            long lastReject = lastRejectCountMap.getOrDefault(threadPoolId, rejectCount);
            int deltaReject = (int) Math.max(0, rejectCount - lastReject);
            lastRejectCountMap.put(threadPoolId, rejectCount);

            ThreadPoolLogRecord record = new ThreadPoolLogRecord()
                    .threadPoolId(threadPoolId)
                    .instanceId(this.instanceId)
                    .timestamp(System.currentTimeMillis())
                    .corePoolSize(executor.getCorePoolSize())
                    .maximumPoolSize(executor.getMaximumPoolSize())
                    .poolSize(executor.getPoolSize())
                    .activeCount(executor.getActiveCount())
                    .largestPoolSize(executor.getLargestPoolSize())
                    .completedTaskCount(executor.getCompletedTaskCount())
                    .queueSize(executor.getQueue().size())
                    .queueCapacity(runtimeInfo.getQueueCapacity())
                    .queueRemainingCapacity(executor.getQueue().remainingCapacity())
                    .rejectCount(deltaReject)
                    .rejectedHandler(executor.getRejectedExecutionHandler().getClass().getSimpleName());

            logStore.append(record);
        }
    }
}

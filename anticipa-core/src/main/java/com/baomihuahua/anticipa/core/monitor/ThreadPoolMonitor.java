package com.baomihuahua.anticipa.core.monitor;

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
 * 线程池运行时数据采集、监控与上报
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

    /**
     * 启动监控任务
     */
    public void start() {
        scheduler.scheduleWithFixedDelay(this::collect, 0, 10, TimeUnit.SECONDS);
    }

    /**
     * 停止监控任务
     */
    public void stop() {
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
        }
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
        }
    }
}

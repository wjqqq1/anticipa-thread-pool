package com.baomihuahua.anticipa.dashboard.dev.starter.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.net.Ipv4Util;
import cn.hutool.core.util.ReflectUtil;
import com.baomihuahua.anticipa.core.executor.AnticipaExecutor;
import com.baomihuahua.anticipa.core.executor.AnticipaRegistry;
import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorHolder;
import com.baomihuahua.anticipa.dashboard.dev.starter.dto.ThreadPoolDashBoardDevBaseMetricsRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.starter.dto.ThreadPoolDashBoardDevRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.baomihuahua.anticipa.dashboard.dev.starter.toolkit.MemoryUtil.getFreeMemory;
import static com.baomihuahua.anticipa.dashboard.dev.starter.toolkit.MemoryUtil.getMemoryProportion;

@Slf4j
public class DynamicThreadPoolService {

    @Value("${server.port:8080}")
    private String port;
    @Value("${spring.profiles.active:unknown}")
    private String activeProfile;

    /**
     * 获取线程池的轻量级运行指标（无锁，适合高频调用）
     *
     * @param threadPoolId 线程池唯一标识
     * @return 线程池简化视图，仅包含关键运行时指标
     */
    public ThreadPoolDashBoardDevBaseMetricsRespDTO getBasicMetrics(String threadPoolId) {
        ThreadPoolExecutorHolder holder = AnticipaRegistry.getHolder(threadPoolId);
        Optional.ofNullable(holder).orElseThrow(() -> new RuntimeException("No thread pool with id " + threadPoolId));

        ThreadPoolExecutor executor = holder.getExecutor();
        int corePoolSize = executor.getCorePoolSize();
        int maximumPoolSize = executor.getMaximumPoolSize();
        long keepAliveTime = executor.getKeepAliveTime(TimeUnit.SECONDS);

        BlockingQueue<?> blockingQueue = executor.getQueue();
        int blockingQueueSize = blockingQueue.size();
        int remainingCapacity = blockingQueue.remainingCapacity();
        int queueCapacity = blockingQueueSize + remainingCapacity;
        String rejectedExecutionHandlerName = executor.getRejectedExecutionHandler().toString();

        long rejectCount = -1L;
        if (executor instanceof AnticipaExecutor) {
            rejectCount = ((AnticipaExecutor) executor).getRejectCount().get();
        }

        return ThreadPoolDashBoardDevBaseMetricsRespDTO.builder()
                .threadPoolId(threadPoolId)
                .corePoolSize(corePoolSize)
                .maximumPoolSize(maximumPoolSize)
                .keepAliveTime(keepAliveTime)
                .workQueueName(blockingQueue.getClass().getSimpleName())
                .workQueueSize(blockingQueueSize)
                .workQueueRemainingCapacity(remainingCapacity)
                .workQueueCapacity(queueCapacity)
                .rejectedHandlerName(rejectedExecutionHandlerName)
                .rejectCount(rejectCount)
                .activeProfile(activeProfile.toUpperCase())
                .networkAddress(Ipv4Util.LOCAL_IP + ":" + port)
                .build();
    }

    /**
     * 获取线程池的完整运行时状态（可能涉及锁操作，不建议高频调用）
     *
     * @param threadPoolId 线程池唯一标识
     * @return 完整的线程池运行状态信息
     */
    public ThreadPoolDashBoardDevRespDTO getRuntimeInfo(String threadPoolId) {
        ThreadPoolExecutorHolder holder = AnticipaRegistry.getHolder(threadPoolId);
        Optional.ofNullable(holder).orElseThrow(() -> new RuntimeException("No thread pool with id " + threadPoolId));

        ThreadPoolExecutor executor = holder.getExecutor();
        BlockingQueue<?> queue = executor.getQueue();

        long rejectCount = -1L;
        if (executor instanceof AnticipaExecutor) {
            rejectCount = ((AnticipaExecutor) executor).getRejectCount().get();
        }

        int workQueueSize = queue.size(); // API 有锁，避免高频率调用
        int remainingCapacity = queue.remainingCapacity(); // API 有锁，避免高频率调用
        return ThreadPoolDashBoardDevRespDTO.builder()
                .threadPoolId(holder.getThreadPoolId())
                .corePoolSize(executor.getCorePoolSize())
                .maximumPoolSize(executor.getMaximumPoolSize())
                .activePoolSize(executor.getActiveCount())  // API 有锁，避免高频率调用
                .currentPoolSize(executor.getPoolSize())  // API 有锁，避免高频率调用
                .completedTaskCount(executor.getCompletedTaskCount())  // API 有锁，避免高频率调用
                .largestPoolSize(executor.getLargestPoolSize())  // API 有锁，避免高频率调用
                .workQueueName(queue.getClass().getSimpleName())
                .workQueueSize(workQueueSize)
                .workQueueRemainingCapacity(remainingCapacity)
                .workQueueCapacity(workQueueSize + remainingCapacity)
                .rejectedHandlerName(executor.getRejectedExecutionHandler().toString())
                .rejectCount(rejectCount)
                .activeProfile(activeProfile.toUpperCase())
                .ip(Ipv4Util.LOCAL_IP)
                .keepAliveTime(executor.getKeepAliveTime(TimeUnit.SECONDS))
                .port(port)
                .currentLoad((int) Math.round((executor.getActiveCount() * 100.0) / executor.getMaximumPoolSize()) + "%")
                .peakLoad((int) Math.round((executor.getLargestPoolSize() * 100.0) / executor.getMaximumPoolSize()) + "%")
                .freeMemory(getFreeMemory())
                .memoryUsagePercentage(getMemoryProportion())
                .status(getThreadPoolState(executor))
                .currentTime(DateUtil.now())
                .build();
    }


    private String getThreadPoolState(ThreadPoolExecutor executor) {
        try {
            Method runStateLessThan = ReflectUtil.getMethodByName(ThreadPoolExecutor.class, "runStateLessThan");
            ReflectUtil.setAccessible(runStateLessThan);
            AtomicInteger ctl = (AtomicInteger) ReflectUtil.getFieldValue(executor, "ctl");
            int shutdown = (int) ReflectUtil.getFieldValue(executor, "SHUTDOWN");
            boolean runStateLessThanBool = ReflectUtil.invoke(executor, runStateLessThan, ctl.get(), shutdown);
            if (runStateLessThanBool) {
                return "Running";
            }

            Method runStateAtLeast = ReflectUtil.getMethodByName(ThreadPoolExecutor.class, "runStateAtLeast");
            ReflectUtil.setAccessible(runStateAtLeast);
            int terminated = (int) ReflectUtil.getFieldValue(executor, "TERMINATED");
            String resultStatus = ReflectUtil.invoke(executor, runStateAtLeast, ctl.get(), terminated) ? "Terminated" : "Shutting down";
            return resultStatus;
        } catch (Exception ex) {
            log.error("Failed to get thread pool status.", ex);
        }

        return "UNKNOWN";
    }
}

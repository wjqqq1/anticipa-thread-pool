package com.baomihuahua.anticipa.core.executor;

import com.baomihuahua.anticipa.core.executor.support.RejectedProxyInvocationHandler;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Proxy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 增强的动态、报警和受监控的线程池 oneThread
 */
@Slf4j
public class AnticipaExecutor extends ThreadPoolExecutor {

    /**
     * 线程池唯一标识，用来动态变更参数等
     */
    @Getter
    private final String threadPoolId;

    /**
     * 线程池拒绝策略执行次数
     */
    @Getter
    private final AtomicLong rejectCount = new AtomicLong();

    /**
     * 等待终止时间，单位毫秒
     */
    private long awaitTerminationMillis;

    /**
     * Creates a new {@code ExtensibleThreadPoolExecutor} with the given initial parameters.
     *
     * @param threadPoolId           thread-pool id
     * @param corePoolSize           the number of threads to keep in the pool, even
     *                               if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize        the maximum number of threads to allow in the
     *                               pool
     * @param keepAliveTime          when the number of threads is greater than
     *                               the core, this is the maximum time that excess idle threads
     *                               will wait for new tasks before terminating.
     * @param unit                   the time unit for the {@code keepAliveTime} argument
     * @param workQueue              the queue to use for holding tasks before they are
     *                               executed.  This queue will hold only the {@code Runnable}
     *                               tasks submitted by the {@code execute} method.
     * @param threadFactory          the factory to use when the executor
     *                               creates a new thread
     * @param handler                the handler to use when execution is blocked
     *                               because the thread bounds and queue capacities are reached
     * @param awaitTerminationMillis the maximum time to wait
     * @throws IllegalArgumentException if one of the following holds:<br>
     *                                  {@code corePoolSize < 0}<br>
     *                                  {@code keepAliveTime < 0}<br>
     *                                  {@code maximumPoolSize <= 0}<br>
     *                                  {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException     if {@code workQueue} or {@code unit}
     *                                  or {@code threadFactory} or {@code handler} is null
     */
    public AnticipaExecutor(
            @NonNull String threadPoolId,
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            @NonNull TimeUnit unit,
            @NonNull BlockingQueue<Runnable> workQueue,
            @NonNull ThreadFactory threadFactory,
            @NonNull RejectedExecutionHandler handler,
            long awaitTerminationMillis) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);

        // 通过动态代理设置拒绝策略执行次数
        setRejectedExecutionHandler(handler);

        // 设置动态线程池扩展属性：线程池 ID 标识
        this.threadPoolId = threadPoolId;

        // 设置等待终止时间，单位毫秒
        this.awaitTerminationMillis = awaitTerminationMillis;
    }

    /**
     * 当前采用轻量级的 Lambda 静态代理方式实现增强，同时也支持使用基于 JDK 动态代理机制的拒绝策略替换方案
     * <pre>
     * RejectedExecutionHandler rejectedProxy = (RejectedExecutionHandler) Proxy
     *         .newProxyInstance(
     *                 handler.getClass().getClassLoader(),
     *                 new Class[]{RejectedExecutionHandler.class},
     *                 new RejectedProxyInvocationHandler(handler, rejectCount)
     *         );
     * </pre>
     */
    @Override
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        RejectedExecutionHandler handlerWrapper = new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                rejectCount.incrementAndGet();
                handler.rejectedExecution(r, executor);
            }

            @Override
            public String toString() {
                return handler.getClass().getSimpleName();
            }
        };

        super.setRejectedExecutionHandler(handlerWrapper);
    }

    @Override
    public void shutdown() {
        if (isShutdown()) {
            return;
        }

        super.shutdown();
        if (this.awaitTerminationMillis <= 0) {
            return;
        }

        log.info("Before shutting down ExecutorService {}", threadPoolId);
        try {
            boolean isTerminated = this.awaitTermination(this.awaitTerminationMillis, TimeUnit.MILLISECONDS);
            if (!isTerminated) {
                log.warn("Timed out while waiting for executor {} to terminate.", threadPoolId);
            } else {
                log.info("ExecutorService {} has been shutdown.", threadPoolId);
            }
        } catch (InterruptedException ex) {
            log.warn("Interrupted while waiting for executor {} to terminate.", threadPoolId);
            Thread.currentThread().interrupt();
        }
    }
}

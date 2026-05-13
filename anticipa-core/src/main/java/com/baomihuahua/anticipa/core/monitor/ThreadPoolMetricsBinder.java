package com.baomihuahua.anticipa.core.monitor;

import com.baomihuahua.anticipa.core.executor.AnticipaRegistry;
import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorHolder;
import com.baomihuahua.anticipa.core.executor.support.ResizableCapacityLinkedBlockingQueue;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 将线程池运行时指标绑定到 Micrometer MeterRegistry，
 * 通过 /actuator/prometheus 端点对外暴露 Prometheus 格式指标。
 * <p>
 * 指标命名规范: dynamic_thread_pool_{metric}
 * 标签: application_name, dynamic_thread_pool_id
 * </p>
 */
@Slf4j
public class ThreadPoolMetricsBinder implements MeterBinder {

    private static final String METRIC_PREFIX = "dynamic_thread_pool";
    private final String applicationName;

    public ThreadPoolMetricsBinder(String applicationName) {
        this.applicationName = applicationName;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registerAll(registry);
    }

    /**
     * 注册所有线程池的 Micrometer 指标
     */
    public void registerAll(MeterRegistry registry) {
        for (ThreadPoolExecutorHolder holder : AnticipaRegistry.getAllHolders()) {
            registerPool(registry, holder);
        }
        log.info("[ThreadPoolMetricsBinder] Registered Micrometer metrics for {} thread pools",
                AnticipaRegistry.getAllHolders().size());
    }

    private void registerPool(MeterRegistry registry, ThreadPoolExecutorHolder holder) {
        String poolId = holder.getThreadPoolId();
        ThreadPoolExecutor executor = holder.getExecutor();

        // Gauge 指标：每次采集时从 executor 实时读取
        gauge(registry, "core_pool_size", poolId, executor, ThreadPoolExecutor::getCorePoolSize);
        gauge(registry, "maximum_pool_size", poolId, executor, ThreadPoolExecutor::getMaximumPoolSize);
        gauge(registry, "pool_size", poolId, executor, ThreadPoolExecutor::getPoolSize);
        gauge(registry, "active_count", poolId, executor, ThreadPoolExecutor::getActiveCount);
        gauge(registry, "largest_pool_size", poolId, executor, ThreadPoolExecutor::getLargestPoolSize);
        gauge(registry, "queue_size", poolId, executor, e -> e.getQueue().size());

        // 队列容量（支持 ResizableCapacityLinkedBlockingQueue）
        gauge(registry, "queue_capacity", poolId, executor, e -> {
            if (e.getQueue() instanceof ResizableCapacityLinkedBlockingQueue) {
                return ((ResizableCapacityLinkedBlockingQueue<?>) e.getQueue()).getCapacity();
            }
            return e.getQueue().size() + e.getQueue().remainingCapacity();
        });

        gauge(registry, "queue_remaining_capacity", poolId, executor, e -> e.getQueue().remainingCapacity());

        // Counter 类指标用 Gauge 代理（Monotonically increasing）
        gauge(registry, "completed_task_count", poolId, executor, ThreadPoolExecutor::getCompletedTaskCount);
        gauge(registry, "reject_count", poolId, executor, e ->
                Math.max(0, e.getTaskCount() - e.getCompletedTaskCount()));
    }

    private void gauge(MeterRegistry registry, String metric, String poolId,
                       ThreadPoolExecutor executor, java.util.function.ToDoubleFunction<ThreadPoolExecutor> fn) {
        Gauge.builder(METRIC_PREFIX + "." + metric, executor, fn)
                .tag("application_name", applicationName)
                .tag("dynamic_thread_pool_id", poolId)
                .description("Thread pool " + metric)
                .register(registry);
    }
}

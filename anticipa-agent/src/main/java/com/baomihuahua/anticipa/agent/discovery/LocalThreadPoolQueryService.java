package com.baomihuahua.anticipa.agent.discovery;

import com.baomihuahua.anticipa.core.executor.AnticipaRegistry;
import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 本地线程池查询服务（默认实现）。
 * <p>
 * 直接读取本机 {@link AnticipaRegistry} 中的 ThreadPoolExecutor，
 * 适用于 Agent 嵌入客户端应用时的本地查询场景。
 * namespace / serviceName 参数在本地模式下忽略。
 * </p>
 */
public class LocalThreadPoolQueryService implements ThreadPoolQueryService {

    @Override
    public Map<String, Object> queryPoolMetrics(String namespace, String serviceName, String threadPoolId, String instanceId) {
        ThreadPoolExecutorHolder holder = AnticipaRegistry.getHolder(threadPoolId);
        if (holder == null) {
            return null;
        }

        ThreadPoolExecutor executor = holder.getExecutor();
        BlockingQueue<Runnable> queue = executor.getQueue();

        Map<String, Object> data = new HashMap<>();
        data.put("threadPoolId", threadPoolId);
        data.put("corePoolSize", executor.getCorePoolSize());
        data.put("maximumPoolSize", executor.getMaximumPoolSize());
        data.put("activeCount", executor.getActiveCount());
        data.put("poolSize", executor.getPoolSize());
        data.put("largestPoolSize", executor.getLargestPoolSize());
        data.put("queueSize", queue.size());
        data.put("queueCapacity", getQueueCapacity(queue));
        data.put("completedTaskCount", executor.getCompletedTaskCount());
        data.put("taskCount", executor.getTaskCount());
        data.put("keepAliveSeconds", executor.getKeepAliveTime(TimeUnit.SECONDS));
        data.put("rejectedHandler", executor.getRejectedExecutionHandler() != null
                ? executor.getRejectedExecutionHandler().getClass().getSimpleName() : "null");

        if (holder.getExecutorProperties() != null) {
            data.put("businessType", holder.getExecutorProperties().getBusinessType());
        }

        int queueUsage = 0;
        int queueCap = getQueueCapacity(queue);
        if (queueCap > 0) {
            queueUsage = queue.size() * 100 / queueCap;
        }
        data.put("queueUsagePercent", queueUsage);

        return data;
    }

    @Override
    public Map<String, Object> adjustPool(String namespace, String serviceName, String threadPoolId, String instanceId, Map<String, Object> params) {
        return Map.of("error", "本地模式不支持运行时调整，请使用远程控制台模式");
    }

    @Override
    public List<String> listPoolIds() {
        return AnticipaRegistry.getAllHolders().stream()
                .map(ThreadPoolExecutorHolder::getThreadPoolId)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> updateConfig(String namespace, String dataId, String group,
                                             String threadPoolId, Map<String, Object> params) {
        return Map.of("error", "Nacos 配置更新在本地模式下不支持，请使用远程控制台模式");
    }

    private int getQueueCapacity(BlockingQueue<?> queue) {
        if (queue instanceof java.util.concurrent.LinkedBlockingQueue) {
            return ((java.util.concurrent.LinkedBlockingQueue<?>) queue).remainingCapacity() + queue.size();
        }
        if (queue instanceof java.util.concurrent.ArrayBlockingQueue) {
            return ((java.util.concurrent.ArrayBlockingQueue<?>) queue).remainingCapacity() + queue.size();
        }
        try {
            java.lang.reflect.Method m = queue.getClass().getMethod("capacity");
            Object val = m.invoke(queue);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
        } catch (Exception ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Method m = queue.getClass().getMethod("getCapacity");
            Object val = m.invoke(queue);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return queue.size() + queue.remainingCapacity();
    }
}

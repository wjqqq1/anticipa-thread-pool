package com.baomihuahua.anticipa.config.common.starter.refresher;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import com.baomihuahua.anticipa.core.executor.AnticipaExecutor;
import com.baomihuahua.anticipa.core.executor.AnticipaRegistry;
import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorHolder;
import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorProperties;
import com.baomihuahua.anticipa.core.executor.support.BlockingQueueTypeEnum;
import com.baomihuahua.anticipa.core.executor.support.RejectedPolicyTypeEnum;
import com.baomihuahua.anticipa.core.executor.support.ResizableCapacityLinkedBlockingQueue;
import com.baomihuahua.anticipa.core.notification.dto.ThreadPoolConfigChangeDTO;
import com.baomihuahua.anticipa.core.notification.service.NotifierDispatcher;
import com.baomihuahua.anticipa.spring.base.support.ApplicationContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.baomihuahua.anticipa.core.constant.Constants.CHANGE_DELIMITER;
import static com.baomihuahua.anticipa.core.constant.Constants.CHANGE_THREAD_POOL_TEXT;

/**
 * 动态线程池监听配置中心刷新事件
 */
@Slf4j
@RequiredArgsConstructor
public class DynamicThreadPoolRefreshListener implements ApplicationListener<ThreadPoolConfigUpdateEvent> {

    private final NotifierDispatcher notifierDispatcher;

    @Override
    public void onApplicationEvent(ThreadPoolConfigUpdateEvent event) {
        BootstrapConfigProperties refresherProperties = event.getBootstrapConfigProperties();

        // 检查远程配置文件是否包含线程池配置
        if (CollUtil.isEmpty(refresherProperties.getExecutors())) {
            return;
        }

        // 刷新动态线程池对象核心参数
        for (ThreadPoolExecutorProperties remoteProperties : refresherProperties.getExecutors()) {
            String threadPoolId = remoteProperties.getThreadPoolId();
            // 以线程池 ID 为粒度加锁，避免多个线程同时刷新同一个线程池
            synchronized (threadPoolId.intern()) {
                ThreadPoolExecutorHolder holder = AnticipaRegistry.getHolder(threadPoolId);

                if (holder == null) {
                    // 远程配置中有但本地没有注册此线程池 → 自动创建
                    log.info("[DynamicRefresh] Thread pool '{}' not found in registry, auto-creating from remote config.", threadPoolId);
                    try {
                        AnticipaExecutor executor = createExecutorFromProperties(remoteProperties);
                        AnticipaRegistry.putHolder(threadPoolId, executor, remoteProperties);
                        log.info("[DynamicRefresh] Auto-created thread pool '{}' from remote config: core={}, max={}",
                                threadPoolId, remoteProperties.getCorePoolSize(), remoteProperties.getMaximumPoolSize());
                    } catch (Exception e) {
                        log.error("[DynamicRefresh] Failed to auto-create thread pool '{}' from remote config", threadPoolId, e);
                    }
                    continue;
                }

                // 检查线程池配置是否发生变化（与当前内存中的配置对比）
                boolean changed = hasThreadPoolConfigChanged(remoteProperties);
                if (!changed) {
                    log.info("[DynamicRefresh] Thread pool '{}' config unchanged. remote: core={}, max={}; registry: core={}, max={}",
                            threadPoolId,
                            remoteProperties.getCorePoolSize(), remoteProperties.getMaximumPoolSize(),
                            holder.getExecutorProperties().getCorePoolSize(), holder.getExecutorProperties().getMaximumPoolSize());
                    continue;
                }

                // 将远程配置应用到线程池，更新相关参数
                updateThreadPoolFromRemoteConfig(remoteProperties);

                // 线程池参数变更后进行日志打印
                ThreadPoolExecutorProperties originalProperties = holder.getExecutorProperties();
                holder.setExecutorProperties(remoteProperties);

                // 发送线程池配置变更消息通知
                sendThreadPoolConfigChangeMessage(originalProperties, remoteProperties);

                // 打印诊断日志：对照配置变化和线程池运行时实际值，便于排查“配置已变更但运行参数未生效”
                logExecutorRuntimeRefreshDiagnostic(threadPoolId, originalProperties, remoteProperties, holder.getExecutor());

                // 打印线程池配置变更日志
                log.info(CHANGE_THREAD_POOL_TEXT,
                        threadPoolId,
                        String.format(CHANGE_DELIMITER, originalProperties.getCorePoolSize(), remoteProperties.getCorePoolSize()),
                        String.format(CHANGE_DELIMITER, originalProperties.getMaximumPoolSize(), remoteProperties.getMaximumPoolSize()),
                        String.format(CHANGE_DELIMITER, originalProperties.getQueueCapacity(), remoteProperties.getQueueCapacity()),
                        String.format(CHANGE_DELIMITER, originalProperties.getKeepAliveTime(), remoteProperties.getKeepAliveTime()),
                        String.format(CHANGE_DELIMITER, originalProperties.getRejectedHandler(), remoteProperties.getRejectedHandler()),
                        String.format(CHANGE_DELIMITER, originalProperties.getAllowCoreThreadTimeOut(), remoteProperties.getAllowCoreThreadTimeOut())
                );
            }
        }
    }

    private boolean hasThreadPoolConfigChanged(ThreadPoolExecutorProperties remoteProperties) {
        String threadPoolId = remoteProperties.getThreadPoolId();
        ThreadPoolExecutorHolder holder = AnticipaRegistry.getHolder(threadPoolId);
        if (holder == null) {
            log.warn("No thread pool found for thread pool id: {}", threadPoolId);
            return false;
        }
        ThreadPoolExecutor executor = holder.getExecutor();
        ThreadPoolExecutorProperties originalProperties = holder.getExecutorProperties();

        return hasDifference(originalProperties, remoteProperties, executor);
    }

    private void updateThreadPoolFromRemoteConfig(ThreadPoolExecutorProperties remoteProperties) {
        String threadPoolId = remoteProperties.getThreadPoolId();
        ThreadPoolExecutorHolder holder = AnticipaRegistry.getHolder(threadPoolId);
        ThreadPoolExecutor executor = holder.getExecutor();
        ThreadPoolExecutorProperties originalProperties = holder.getExecutorProperties();

        Integer remoteCorePoolSize = remoteProperties.getCorePoolSize();
        Integer remoteMaximumPoolSize = remoteProperties.getMaximumPoolSize();
        if (remoteCorePoolSize != null && remoteMaximumPoolSize != null) {
            int originalMaximumPoolSize = executor.getMaximumPoolSize();
            if (remoteCorePoolSize > originalMaximumPoolSize) {
                executor.setMaximumPoolSize(remoteMaximumPoolSize);
                executor.setCorePoolSize(remoteCorePoolSize);
            } else {
                executor.setCorePoolSize(remoteCorePoolSize);
                executor.setMaximumPoolSize(remoteMaximumPoolSize);
            }
        } else {
            if (remoteMaximumPoolSize != null) {
                executor.setMaximumPoolSize(remoteMaximumPoolSize);
            }
            if (remoteCorePoolSize != null) {
                executor.setCorePoolSize(remoteCorePoolSize);
            }
        }

        if (remoteProperties.getAllowCoreThreadTimeOut() != null &&
                !Objects.equals(remoteProperties.getAllowCoreThreadTimeOut(), originalProperties.getAllowCoreThreadTimeOut())) {
            executor.allowCoreThreadTimeOut(remoteProperties.getAllowCoreThreadTimeOut());
        }

        if (remoteProperties.getRejectedHandler() != null &&
                !Objects.equals(remoteProperties.getRejectedHandler(), originalProperties.getRejectedHandler())) {
            RejectedExecutionHandler handler = RejectedPolicyTypeEnum.createPolicy(remoteProperties.getRejectedHandler());
            executor.setRejectedExecutionHandler(handler);
        }

        if (remoteProperties.getKeepAliveTime() != null &&
                !Objects.equals(remoteProperties.getKeepAliveTime(), originalProperties.getKeepAliveTime())) {
            executor.setKeepAliveTime(remoteProperties.getKeepAliveTime(), TimeUnit.SECONDS);
        }

        // 更新队列容量（仅对 ResizableCapacityLinkedBlockingQueue 生效）
        if (isQueueCapacityChanged(originalProperties, remoteProperties, executor)) {
            BlockingQueue<Runnable> queue = executor.getQueue();
            ResizableCapacityLinkedBlockingQueue<?> resizableQueue = (ResizableCapacityLinkedBlockingQueue<?>) queue;
            resizableQueue.setCapacity(remoteProperties.getQueueCapacity());
        }
    }

    private boolean hasDifference(ThreadPoolExecutorProperties originalProperties,
                                  ThreadPoolExecutorProperties remoteProperties,
                                  ThreadPoolExecutor executor) {
        return isChanged(originalProperties.getCorePoolSize(), remoteProperties.getCorePoolSize())
                || isChanged(originalProperties.getMaximumPoolSize(), remoteProperties.getMaximumPoolSize())
                || isChanged(originalProperties.getAllowCoreThreadTimeOut(), remoteProperties.getAllowCoreThreadTimeOut())
                || isChanged(originalProperties.getKeepAliveTime(), remoteProperties.getKeepAliveTime())
                || isChanged(originalProperties.getRejectedHandler(), remoteProperties.getRejectedHandler())
                || isQueueCapacityChanged(originalProperties, remoteProperties, executor);
    }

    private <T> boolean isChanged(T before, T after) {
        return after != null && !Objects.equals(before, after);
    }

    private boolean isQueueCapacityChanged(ThreadPoolExecutorProperties originalProperties,
                                           ThreadPoolExecutorProperties remoteProperties,
                                           ThreadPoolExecutor executor) {
        Integer remoteCapacity = remoteProperties.getQueueCapacity();
        Integer originalCapacity = originalProperties.getQueueCapacity();
        BlockingQueue<?> queue = executor.getQueue();

        return remoteCapacity != null
                && !Objects.equals(remoteCapacity, originalCapacity)
                && Objects.equals(BlockingQueueTypeEnum.RESIZABLE_CAPACITY_LINKED_BLOCKING_QUEUE.getName(), queue.getClass().getSimpleName());
    }

    @SneakyThrows
    private void sendThreadPoolConfigChangeMessage(ThreadPoolExecutorProperties originalProperties,
                                                   ThreadPoolExecutorProperties remoteProperties) {
        Environment environment = ApplicationContextHolder.getBean(Environment.class);
        String activeProfile = environment.getProperty("spring.profiles.active", "dev");
        String applicationName = environment.getProperty("spring.application.name");

        Map<String, ThreadPoolConfigChangeDTO.ChangePair<?>> changes = new HashMap<>();
        changes.put("corePoolSize", new ThreadPoolConfigChangeDTO.ChangePair<>(originalProperties.getCorePoolSize(), remoteProperties.getCorePoolSize()));
        changes.put("maximumPoolSize", new ThreadPoolConfigChangeDTO.ChangePair<>(originalProperties.getMaximumPoolSize(), remoteProperties.getMaximumPoolSize()));
        changes.put("queueCapacity", new ThreadPoolConfigChangeDTO.ChangePair<>(originalProperties.getQueueCapacity(), remoteProperties.getQueueCapacity()));
        changes.put("rejectedHandler", new ThreadPoolConfigChangeDTO.ChangePair<>(originalProperties.getRejectedHandler(), remoteProperties.getRejectedHandler()));
        changes.put("keepAliveTime", new ThreadPoolConfigChangeDTO.ChangePair<>(originalProperties.getKeepAliveTime(), remoteProperties.getKeepAliveTime()));

        // 安全获取 receives：remoteProperties.notify 可能为 null
        String receives = null;
        if (remoteProperties.getNotify() != null) {
            receives = remoteProperties.getNotify().getReceives();
        }
        if (receives == null && originalProperties.getNotify() != null) {
            receives = originalProperties.getNotify().getReceives();
        }

        // 队列类型：优先使用 remoteProperties，为 null 时回退到 originalProperties
        String queueType = remoteProperties.getWorkQueue() != null
                ? remoteProperties.getWorkQueue()
                : originalProperties.getWorkQueue();

        ThreadPoolConfigChangeDTO configChangeDTO = ThreadPoolConfigChangeDTO.builder()
                .activeProfile(activeProfile)
                .identify(InetAddress.getLocalHost().getHostAddress())
                .applicationName(applicationName)
                .threadPoolId(originalProperties.getThreadPoolId())
                .receives(receives)
                .workQueue(queueType)
                .changes(changes)
                .updateTime(DateUtil.now())
                // 钉钉通知模板所需的字段 —— 优先使用远程配置值，为 null 时回退到原始配置值
                .corePoolSize(remoteProperties.getCorePoolSize() != null ? remoteProperties.getCorePoolSize() : originalProperties.getCorePoolSize())
                .maximumPoolSize(remoteProperties.getMaximumPoolSize() != null ? remoteProperties.getMaximumPoolSize() : originalProperties.getMaximumPoolSize())
                .keepAliveTime(remoteProperties.getKeepAliveTime() != null ? remoteProperties.getKeepAliveTime() : originalProperties.getKeepAliveTime())
                .queueType(queueType)
                .queueCapacity(remoteProperties.getQueueCapacity() != null ? remoteProperties.getQueueCapacity() : originalProperties.getQueueCapacity())
                .oldRejectedType(originalProperties.getRejectedHandler())
                .rejectedType(remoteProperties.getRejectedHandler() != null ? remoteProperties.getRejectedHandler() : originalProperties.getRejectedHandler())
                .changeTime(DateUtil.now())
                .build();
        notifierDispatcher.sendChangeMessage(configChangeDTO);
    }

    private void logExecutorRuntimeRefreshDiagnostic(String threadPoolId,
                                                     ThreadPoolExecutorProperties originalProperties,
                                                     ThreadPoolExecutorProperties remoteProperties,
                                                     ThreadPoolExecutor executor) {
        log.info("[DynamicRefreshDiagnostic] threadPoolId='{}' applied refresh. " +
                        "config(core:{}->{}, max:{}->{}, queue:{}->{}, keepAlive:{}->{}, timeout:{}->{}, reject:{}->{}), " +
                        "runtime(core={}, max={}, queueSize={}, queueRemainingCapacity={}, active={}, poolSize={}, largestPoolSize={})",
                threadPoolId,
                originalProperties.getCorePoolSize(), remoteProperties.getCorePoolSize(),
                originalProperties.getMaximumPoolSize(), remoteProperties.getMaximumPoolSize(),
                originalProperties.getQueueCapacity(), remoteProperties.getQueueCapacity(),
                originalProperties.getKeepAliveTime(), remoteProperties.getKeepAliveTime(),
                originalProperties.getAllowCoreThreadTimeOut(), remoteProperties.getAllowCoreThreadTimeOut(),
                originalProperties.getRejectedHandler(), remoteProperties.getRejectedHandler(),
                executor.getCorePoolSize(), executor.getMaximumPoolSize(),
                executor.getQueue().size(), executor.getQueue().remainingCapacity(),
                executor.getActiveCount(), executor.getPoolSize(), executor.getLargestPoolSize());
    }

    /**
     * 根据 ThreadPoolExecutorProperties 创建 AnticipaExecutor 实例（运行时动态创建）。
     */
    private AnticipaExecutor createExecutorFromProperties(ThreadPoolExecutorProperties props) {
        int corePoolSize = props.getCorePoolSize() != null ? props.getCorePoolSize() : Runtime.getRuntime().availableProcessors();
        int maxPoolSize = props.getMaximumPoolSize() != null ? props.getMaximumPoolSize() : corePoolSize + (corePoolSize >> 1);
        long keepAliveTime = props.getKeepAliveTime() != null ? props.getKeepAliveTime() : 30000L;
        String workQueueName = props.getWorkQueue() != null ? props.getWorkQueue() : BlockingQueueTypeEnum.LINKED_BLOCKING_QUEUE.getName();
        Integer queueCapacity = props.getQueueCapacity() != null ? props.getQueueCapacity() : 4096;
        boolean allowCoreThreadTimeOut = props.getAllowCoreThreadTimeOut() != null ? props.getAllowCoreThreadTimeOut() : false;

        BlockingQueue<Runnable> workQueue = BlockingQueueTypeEnum.createBlockingQueue(workQueueName, queueCapacity);
        java.util.concurrent.ThreadFactory threadFactory = com.baomihuahua.anticipa.core.toolkit.ThreadFactoryBuilder.builder()
                .namePrefix(props.getThreadPoolId() + "_")
                .build();

        java.util.concurrent.RejectedExecutionHandler rejectedHandler;
        if (props.getRejectedHandler() != null) {
            rejectedHandler = RejectedPolicyTypeEnum.createPolicy(props.getRejectedHandler());
        } else {
            rejectedHandler = new java.util.concurrent.ThreadPoolExecutor.AbortPolicy();
        }

        AnticipaExecutor executor = new AnticipaExecutor(
                props.getThreadPoolId(),
                corePoolSize,
                maxPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                workQueue,
                threadFactory,
                rejectedHandler,
                0L
        );

        executor.allowCoreThreadTimeOut(allowCoreThreadTimeOut);
        return executor;
    }
}

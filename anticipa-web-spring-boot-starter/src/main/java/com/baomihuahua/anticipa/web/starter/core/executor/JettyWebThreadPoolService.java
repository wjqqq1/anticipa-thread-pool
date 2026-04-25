package com.baomihuahua.anticipa.web.starter.core.executor;

import cn.hutool.core.util.ReflectUtil;
import com.baomihuahua.anticipa.core.constant.Constants;
import com.baomihuahua.anticipa.web.starter.core.WebContainerEnum;
import com.baomihuahua.anticipa.web.starter.core.WebThreadPoolBaseMetrics;
import com.baomihuahua.anticipa.web.starter.core.WebThreadPoolConfig;
import com.baomihuahua.anticipa.web.starter.core.WebThreadPoolState;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.springframework.boot.web.embedded.jetty.JettyWebServer;
import org.springframework.boot.web.server.WebServer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;

/**
 * Jetty 线程池接口
 */
@Slf4j
public class JettyWebThreadPoolService extends AbstractWebThreadPoolService {

    @Override
    protected Executor getExecutor(WebServer webServer) {
        return ((JettyWebServer) webServer).getServer().getThreadPool();
    }

    @Override
    public void updateThreadPool(WebThreadPoolConfig config) {
        try {
            QueuedThreadPool jettyExecutor = (QueuedThreadPool) executor;
            int originalCorePoolSize = jettyExecutor.getMinThreads();
            int originalMaximumPoolSize = jettyExecutor.getMaxThreads();
            long originalKeepAliveTime = jettyExecutor.getIdleTimeout();

            if (config.getCorePoolSize() > originalMaximumPoolSize) {
                jettyExecutor.setMaxThreads(config.getMaximumPoolSize());
                jettyExecutor.setMinThreads(config.getCorePoolSize());
            } else {
                jettyExecutor.setMinThreads(config.getCorePoolSize());
                jettyExecutor.setMaxThreads(config.getMaximumPoolSize());
            }
            jettyExecutor.setIdleTimeout(config.getKeepAliveTime().intValue());

            log.info("[Jetty] Changed web thread pool. corePoolSize: {}, maximumPoolSize: {}, keepAliveTime: {}",
                    String.format(Constants.CHANGE_DELIMITER, originalCorePoolSize, config.getCorePoolSize()),
                    String.format(Constants.CHANGE_DELIMITER, originalMaximumPoolSize, config.getMaximumPoolSize()),
                    String.format(Constants.CHANGE_DELIMITER, originalKeepAliveTime, config.getKeepAliveTime()));
        } catch (Exception ex) {
            log.error("Failed to modify the Jetty thread pool parameter.", ex);
        }
    }

    @SneakyThrows
    @Override
    public WebThreadPoolBaseMetrics getBasicMetrics() {
        QueuedThreadPool jettyExecutor = (QueuedThreadPool) executor;
        int corePoolSize = jettyExecutor.getMinThreads();
        int maximumPoolSize = jettyExecutor.getMaxThreads();
        long keepAliveTime = jettyExecutor.getIdleTimeout();

        BlockingQueue jobs = (BlockingQueue) ReflectUtil.getFieldValue(jettyExecutor, "_jobs");
        int blockingQueueSize = jettyExecutor.getQueueSize();
        int remainingCapacity = jobs.remainingCapacity();
        int queueCapacity = blockingQueueSize + remainingCapacity;
        String rejectedExecutionHandlerName = "JettyRejectedExecutionHandler";

        return WebThreadPoolBaseMetrics.builder()
                .corePoolSize(corePoolSize)
                .maximumPoolSize(maximumPoolSize)
                .keepAliveTime(keepAliveTime)
                .workQueueName(jobs.getClass().getSimpleName())
                .workQueueSize(blockingQueueSize)
                .workQueueRemainingCapacity(remainingCapacity)
                .workQueueCapacity(queueCapacity)
                .rejectedHandlerName(rejectedExecutionHandlerName)
                .build();
    }

    @Override
    public WebThreadPoolState getRuntimeState() {
        QueuedThreadPool jettyExecutor = (QueuedThreadPool) executor;
        int corePoolSize = jettyExecutor.getMinThreads();
        int maximumPoolSize = jettyExecutor.getMaxThreads();
        int activeCount = jettyExecutor.getBusyThreads();
        int currentPoolSize  = jettyExecutor.getThreads();
        long keepAliveTime = jettyExecutor.getIdleTimeout();

        BlockingQueue jobs = (BlockingQueue) ReflectUtil.getFieldValue(jettyExecutor, "_jobs");
        int blockingQueueSize = jettyExecutor.getQueueSize();
        int remainingCapacity = jobs.remainingCapacity();
        int queueCapacity = blockingQueueSize + remainingCapacity;
        String rejectedExecutionHandlerName = "JettyRejectedExecutionHandler";

        // 相对于 Tomcat 来说，Jetty 的线程池个性化更多，这会导致 WebThreadPoolState 并不完整
        return WebThreadPoolState.builder()
                .corePoolSize(corePoolSize)
                .maximumPoolSize(maximumPoolSize)
                .activePoolSize(activeCount)
                .currentPoolSize(currentPoolSize)
                .keepAliveTime(keepAliveTime)
                .workQueueName(jobs.getClass().getSimpleName())
                .workQueueSize(blockingQueueSize)
                .workQueueRemainingCapacity(remainingCapacity)
                .workQueueCapacity(queueCapacity)
                .rejectedHandlerName(rejectedExecutionHandlerName)
                .build();
    }

    @Override
    public WebContainerEnum getWebContainerType() {
        return WebContainerEnum.JETTY;
    }
}

package com.baomihuahua.anticipa.web.starter.core.executor;

import com.baomihuahua.anticipa.core.constant.Constants;
import com.baomihuahua.anticipa.web.starter.core.WebContainerEnum;
import com.baomihuahua.anticipa.web.starter.core.WebThreadPoolBaseMetrics;
import com.baomihuahua.anticipa.web.starter.core.WebThreadPoolConfig;
import com.baomihuahua.anticipa.web.starter.core.WebThreadPoolState;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.WebServer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Tomcat 线程池接口
 */
@Slf4j
public class TomcatWebThreadPoolService extends AbstractWebThreadPoolService {

    @Override
    protected Executor getExecutor(WebServer webServer) {
        return ((TomcatWebServer) webServer).getTomcat().getConnector().getProtocolHandler().getExecutor();
    }

    @Override
    public void updateThreadPool(WebThreadPoolConfig config) {
        try {
            ThreadPoolExecutor tomcatExecutor = (ThreadPoolExecutor) executor;
            int originalCorePoolSize = tomcatExecutor.getCorePoolSize();
            int originalMaximumPoolSize = tomcatExecutor.getMaximumPoolSize();
            long originalKeepAliveTime = tomcatExecutor.getKeepAliveTime(TimeUnit.SECONDS);

            if (config.getCorePoolSize() > originalMaximumPoolSize) {
                tomcatExecutor.setMaximumPoolSize(config.getMaximumPoolSize());
                tomcatExecutor.setCorePoolSize(config.getCorePoolSize());
            } else {
                tomcatExecutor.setCorePoolSize(config.getCorePoolSize());
                tomcatExecutor.setMaximumPoolSize(config.getMaximumPoolSize());
            }
            tomcatExecutor.setKeepAliveTime(config.getKeepAliveTime(), TimeUnit.SECONDS);

            log.info("[Tomcat] Changed web thread pool. corePoolSize: {}, maximumPoolSize: {}, keepAliveTime: {}",
                    String.format(Constants.CHANGE_DELIMITER, originalCorePoolSize, config.getCorePoolSize()),
                    String.format(Constants.CHANGE_DELIMITER, originalMaximumPoolSize, config.getMaximumPoolSize()),
                    String.format(Constants.CHANGE_DELIMITER, originalKeepAliveTime, config.getKeepAliveTime()));
        } catch (Exception ex) {
            log.error("Failed to modify the Tomcat thread pool parameter.", ex);
        }
    }

    @Override
    public WebThreadPoolBaseMetrics getBasicMetrics() {
        ThreadPoolExecutor tomcatExecutor = (ThreadPoolExecutor) executor;
        int corePoolSize = tomcatExecutor.getCorePoolSize();
        int maximumPoolSize = tomcatExecutor.getMaximumPoolSize();
        long keepAliveTime = tomcatExecutor.getKeepAliveTime(TimeUnit.SECONDS);

        BlockingQueue<?> blockingQueue = tomcatExecutor.getQueue();
        int blockingQueueSize = blockingQueue.size();
        int remainingCapacity = blockingQueue.remainingCapacity();
        int queueCapacity = blockingQueueSize + remainingCapacity;
        String rejectedExecutionHandlerName = tomcatExecutor.getRejectedExecutionHandler().getClass().getSimpleName();

        return WebThreadPoolBaseMetrics.builder()
                .corePoolSize(corePoolSize)
                .maximumPoolSize(maximumPoolSize)
                .keepAliveTime(keepAliveTime)
                .workQueueName(blockingQueue.getClass().getSimpleName())
                .workQueueSize(blockingQueueSize)
                .workQueueRemainingCapacity(remainingCapacity)
                .workQueueCapacity(queueCapacity)
                .rejectedHandlerName(rejectedExecutionHandlerName)
                .build();
    }

    @Override
    public WebThreadPoolState getRuntimeState() {
        ThreadPoolExecutor tomcatExecutor = (ThreadPoolExecutor) executor;
        int corePoolSize = tomcatExecutor.getCorePoolSize();
        int maximumPoolSize = tomcatExecutor.getMaximumPoolSize();
        int activeCount = tomcatExecutor.getActiveCount();
        long completedTaskCount = tomcatExecutor.getCompletedTaskCount();
        int largestPoolSize = tomcatExecutor.getLargestPoolSize();
        int currentPoolSize = tomcatExecutor.getPoolSize();
        long keepAliveTime = tomcatExecutor.getKeepAliveTime(TimeUnit.SECONDS);

        BlockingQueue<?> blockingQueue = tomcatExecutor.getQueue();
        int blockingQueueSize = blockingQueue.size();
        int remainingCapacity = blockingQueue.remainingCapacity();
        int queueCapacity = blockingQueueSize + remainingCapacity;
        String rejectedExecutionHandlerName = tomcatExecutor.getRejectedExecutionHandler().getClass().getSimpleName();
        return WebThreadPoolState.builder()
                .corePoolSize(corePoolSize)
                .maximumPoolSize(maximumPoolSize)
                .activePoolSize(activeCount)
                .completedTaskCount(completedTaskCount)
                .largestPoolSize(largestPoolSize)
                .currentPoolSize(currentPoolSize)
                .keepAliveTime(keepAliveTime)
                .workQueueName(blockingQueue.getClass().getSimpleName())
                .workQueueSize(blockingQueueSize)
                .workQueueRemainingCapacity(remainingCapacity)
                .workQueueCapacity(queueCapacity)
                .rejectedHandlerName(rejectedExecutionHandlerName)
                .build();
    }

    @Override
    public WebContainerEnum getWebContainerType() {
        return WebContainerEnum.TOMCAT;
    }
}

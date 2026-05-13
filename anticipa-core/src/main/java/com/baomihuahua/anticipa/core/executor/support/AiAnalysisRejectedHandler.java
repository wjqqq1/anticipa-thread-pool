package com.baomihuahua.anticipa.core.executor.support;

import com.baomihuahua.anticipa.core.config.RejectedAnalysisConfig;
import com.baomihuahua.anticipa.core.event.ThreadPoolRejectedEvent;
import com.baomihuahua.anticipa.core.notification.dto.ThreadPoolAlarmNotifyDTO;
import com.baomihuahua.anticipa.core.notification.service.NotifierDispatcher;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 分析拒绝策略。
 * <p>
 * 当线程池触发拒绝时：
 * <ol>
 *   <li>发布 {@link ThreadPoolRejectedEvent} Spring 事件（供 agent 模块消费，触发 AI 日志拉取与分析）</li>
 *   <li>通过 {@link NotifierDispatcher} 发送钉钉告警通知（默认行为）</li>
 * </ol>
 * </p>
 *
 * <p>
 * 此处理器由 Spring 配置类创建并注册到 {@link RejectedPolicyTypeEnum}，
 * 配置项 {@code anticipa.rejected-analysis.ding-talk.webhook-url} 用于指定钉钉 WebHook。
 * </p>
 */
@Slf4j
public class AiAnalysisRejectedHandler implements RejectedExecutionHandler {

    private final RejectedAnalysisConfig config;

    @Setter
    private NotifierDispatcher notifierDispatcher;

    @Setter
    private ApplicationEventPublisher eventPublisher;

    public AiAnalysisRejectedHandler(RejectedAnalysisConfig config) {
        this.config = config;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        // 1. 构建拒绝事件
        String taskClassName = (r != null) ? r.getClass().getName() : "unknown";
        ThreadPoolRejectedEvent event = new ThreadPoolRejectedEvent(
                executor,
                extractThreadPoolId(executor),
                taskClassName,
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getQueue().size(),
                executor.getQueue().size() + executor.getQueue().remainingCapacity(),
                executor.getCompletedTaskCount(),
                executor.getTaskCount()
        );

        log.warn("[AiAnalysisRejectedHandler] Thread pool '{}' rejected task '{}'. {}",
                event.getThreadPoolId(), taskClassName, event);

        // 2. 发布事件（agent 模块的监听器会消费此事件，触发 AI 分析）
        if (eventPublisher != null) {
            try {
                eventPublisher.publishEvent(event);
            } catch (Exception e) {
                log.error("[AiAnalysisRejectedHandler] Failed to publish rejection event for '{}'",
                        event.getThreadPoolId(), e);
            }
        }

        // 3. 发送钉钉通知（默认行为）
        if (notifierDispatcher != null && config.getDingTalk().isNotifyOnReject()) {
            try {
                sendDingTalkNotification(event, executor);
            } catch (Exception e) {
                log.error("[AiAnalysisRejectedHandler] Failed to send DingTalk notification for '{}'",
                        event.getThreadPoolId(), e);
            }
        }

        // 4. 记录详细日志
        log.warn("Task rejected. poolId={}, task={}, active={}/{}, queue={}/{}, core={}, max={}",
                event.getThreadPoolId(), taskClassName,
                event.getActiveCount(), event.getMaximumPoolSize(),
                event.getQueueSize(), event.getQueueCapacity(),
                event.getCorePoolSize(), event.getMaximumPoolSize());
    }

    /**
     * 通过 NotifierDispatcher 发送钉钉告警。
     */
    private void sendDingTalkNotification(ThreadPoolRejectedEvent event, ThreadPoolExecutor executor) {
        String hostAddress = "unknown";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
            // ignore
        }

        ThreadPoolAlarmNotifyDTO alarmDTO = ThreadPoolAlarmNotifyDTO.builder()
                .alarmType("REJECT")
                .threadPoolId(event.getThreadPoolId())
                .applicationName(detectApplicationName())
                .activeProfile(detectActiveProfile())
                .identify(hostAddress)
                .corePoolSize(event.getCorePoolSize())
                .maximumPoolSize(event.getMaximumPoolSize())
                .currentPoolSize(event.getPoolSize())
                .activePoolSize(event.getActiveCount())
                .largestPoolSize(executor.getLargestPoolSize())
                .completedTaskCount(event.getCompletedTaskCount())
                .workQueueName(executor.getQueue().getClass().getSimpleName())
                .workQueueCapacity(event.getQueueCapacity())
                .workQueueSize(event.getQueueSize())
                .workQueueRemainingCapacity(event.getQueueRemainingCapacity())
                .rejectedHandlerName(config.getPolicyName())
                .rejectCount(executor.getTaskCount() - executor.getCompletedTaskCount())
                .currentTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .receives(config.getDingTalk().getReceives() != null ? config.getDingTalk().getReceives() : "admin")
                .interval(config.getDingTalk().getInterval())
                .build();

        notifierDispatcher.sendAlarmMessage(alarmDTO);
    }

    private String extractThreadPoolId(ThreadPoolExecutor executor) {
        if (executor instanceof com.baomihuahua.anticipa.core.executor.AnticipaExecutor) {
            return ((com.baomihuahua.anticipa.core.executor.AnticipaExecutor) executor).getThreadPoolId();
        }
        return executor.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(executor));
    }

    private String detectApplicationName() {
        try {
            return System.getProperty("spring.application.name", "unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String detectActiveProfile() {
        try {
            return System.getProperty("spring.profiles.active", "dev");
        } catch (Exception e) {
            return "dev";
        }
    }
}

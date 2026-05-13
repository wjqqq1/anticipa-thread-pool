package com.baomihuahua.anticipa.agent.event;

import com.baomihuahua.anticipa.agent.AgentLoop;
import com.baomihuahua.anticipa.agent.AgentReport;
import com.baomihuahua.anticipa.agent.SilentRequest;
import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import com.baomihuahua.anticipa.core.config.ThreadPoolLogConfig;
import com.baomihuahua.anticipa.core.event.ThreadPoolRejectedEvent;
import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorProperties;
import com.baomihuahua.anticipa.core.monitor.ThreadPoolLogRecord;
import com.baomihuahua.anticipa.core.monitor.ThreadPoolLogStore;
import com.baomihuahua.anticipa.core.notification.dto.ThreadPoolAlarmNotifyDTO;
import com.baomihuahua.anticipa.core.notification.service.NotifierDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 拒绝分析事件监听器。
 * <p>
 * 监听 {@link ThreadPoolRejectedEvent}，在任务被拒绝时触发 AI 分析流程：
 * <ol>
 *   <li>拉取拒绝前后时间窗口的运行日志 {@link ThreadPoolLogStore}</li>
 *   <li>调用 {@link AgentLoop#executeSilent(SilentRequest)} 执行 AI 分析</li>
 *   <li>通过 {@link NotifierDispatcher} 发送分析报告通知（含钉钉）</li>
 * </ol>
 * </p>
 *
 * <p>
 * 分析在异步线程池中执行，不阻塞拒绝处理流程。
 * </p>
 */
public class RejectionAnalysisListener {

    private static final Logger log = LoggerFactory.getLogger(RejectionAnalysisListener.class);

    private final AgentLoop agentLoop;
    private final ThreadPoolLogStore logStore;
    private final ThreadPoolLogConfig logConfig;
    private final NotifierDispatcher notifierDispatcher;

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rejection-analysis");
        t.setDaemon(true);
        return t;
    });

    public RejectionAnalysisListener(AgentLoop agentLoop,
                                      ThreadPoolLogStore logStore,
                                      ThreadPoolLogConfig logConfig,
                                      NotifierDispatcher notifierDispatcher) {
        this.agentLoop = agentLoop;
        this.logStore = logStore;
        this.logConfig = logConfig;
        this.notifierDispatcher = notifierDispatcher;
    }

    /**
     * 处理拒绝事件。
     * <p>
     * 异步执行，不阻塞调用线程。
     * </p>
     */
    @EventListener
    public void onRejected(ThreadPoolRejectedEvent event) {
        log.info("[RejectionAnalysis] Received rejection event for pool '{}', task '{}'. Submitting analysis...",
                event.getThreadPoolId(), event.getTaskClassName());

        analysisExecutor.submit(() -> {
            try {
                processAnalysis(event);
            } catch (Exception e) {
                log.error("[RejectionAnalysis] Analysis failed for pool '{}'",
                        event.getThreadPoolId(), e);
            }
        });
    }

    /**
     * 执行 AI 分析流程。
     */
    private void processAnalysis(ThreadPoolRejectedEvent event) {
        String threadPoolId = event.getThreadPoolId();

        // 1. 拉取拒绝前后时间窗口的日志
        long windowMinutes = logConfig.getQuery().getDefaultWindowMinutes();
        // 以拒绝事件为基准，向前取窗口
        Instant endTime = Instant.ofEpochMilli(event.getTimestamp());
        Instant startTime = endTime.minus(Duration.ofMinutes(windowMinutes));

        List<ThreadPoolLogRecord> logs = logStore.query(
                threadPoolId, startTime, endTime, ThreadPoolLogStore.Aggregation.RAW);

        log.info("[RejectionAnalysis] Fetched {} log records for pool '{}' (window: {} min)",
                logs.size(), threadPoolId, windowMinutes);

        // 2. 获取线程池当前配置
        ThreadPoolExecutorProperties config = getPoolConfig(threadPoolId);

        // 3. 构建静默请求
        SilentRequest request = new SilentRequest();
        request.setThreadPoolId(threadPoolId);
        request.setRecentLogs(logs);
        request.setConfig(config);
        request.setAutoAdjust(false); // 拒绝事件不建议自动调整，仅分析
        if (config != null && config.getBusinessType() != null) {
            request.setBusinessDescription(config.getBusinessType().toDisplayString());
        }

        // 4. 调用 AI 分析
        log.info("[RejectionAnalysis] Starting AI analysis for pool '{}'...", threadPoolId);
        AgentReport report = agentLoop.executeSilent(request);
        log.info("[RejectionAnalysis] AI analysis completed for pool '{}': health={}, summary='{}'",
                threadPoolId, report.getHealthStatus(), report.getSummary());

        // 5. 通过 NotifierDispatcher 发送分析报告通知
        sendAnalysisReport(event, report);
    }

    /**
     * 发送 AI 分析报告通知。
     * <p>
     * 使用 customMessage 字段传递格式化的分析报告，使钉钉等通知渠道可以展示完整的 AI 分析内容。
     * </p>
     */
    private void sendAnalysisReport(ThreadPoolRejectedEvent event, AgentReport report) {
        String hostAddress = "unknown";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
        }

        String analysisSummary = report.getSummary() != null ? report.getSummary() : "分析完成";
        String healthStatus = report.getHealthStatus() != null ? report.getHealthStatus() : "UNKNOWN";
        String healthEmoji = switch (healthStatus) {
            case "HEALTHY" -> "\uD83D\uDFE2";
            case "WARNING" -> "\uD83D\uDFE1";
            case "CRITICAL" -> "\uD83D\uDD34";
            default -> "\u26AA";
        };
        String adjustInfo = report.isAdjustmentRecommended()
                ? "建议调整参数，原因：" + (report.getAdjustReason() != null ? report.getAdjustReason() : "待优化")
                : "无需调整";

        // 构建自定义分析报告 Markdown（钉钉展示）
        String customMessage = String.format("""
                **<font color=#FF0000>[AI 分析] </font>%s - 线程池拒绝分析报告**
                
                 ---
                
                <font color='#708090' size=2>线程池ID：%s</font>\s
                
                <font color='#708090' size=2>应用实例：%s</font>\s
                
                <font color='#708090' size=2>拒绝策略：%s</font>\s
                
                 ---
                
                <font color='#708090' size=2>核心线程数：%d</font>\s
                
                <font color='#708090' size=2>最大线程数：%d</font>\s
                
                <font color='#708090' size=2>当前线程数：%d</font>\s
                
                <font color='#708090' size=2>活跃线程数：%d</font>\s
                
                <font color='#708090' size=2>队列使用率：%d/%d (%d%%)</font>\s
                
                 ---
                
                %s **健康状态：%s** %s
                
                **摘要：%s**
                
                **调整建议：%s**
                
                %s
                
                 ---
                
                **分析时间：%s**
                """,
                detectApplicationName(),
                event.getThreadPoolId(),
                hostAddress,
                "AiAnalysisRejectedPolicy",
                event.getCorePoolSize(),
                event.getMaximumPoolSize(),
                event.getPoolSize(),
                event.getActiveCount(),
                event.getQueueSize(), event.getQueueCapacity(), event.getQueueUsagePercent(),
                healthEmoji, healthStatus, healthEmoji,
                analysisSummary,
                adjustInfo,
                report.getAnalysis() != null ? "> " + report.getAnalysis().replace("\n", "\n> ") : "",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );

        ThreadPoolAlarmNotifyDTO alarmDTO = ThreadPoolAlarmNotifyDTO.builder()
                .alarmType("REJECT_AI_ANALYSIS")
                .threadPoolId(event.getThreadPoolId())
                .applicationName(detectApplicationName())
                .activeProfile(detectActiveProfile())
                .identify(hostAddress)
                .corePoolSize(event.getCorePoolSize())
                .maximumPoolSize(event.getMaximumPoolSize())
                .currentPoolSize(event.getPoolSize())
                .activePoolSize(event.getActiveCount())
                .workQueueCapacity(event.getQueueCapacity())
                .workQueueSize(event.getQueueSize())
                .rejectedHandlerName("AiAnalysisRejectedPolicy")
                .rejectCount(event.getTaskCount() - event.getCompletedTaskCount())
                .currentTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .receives("AI 分析")
                .interval(1)
                .customMessage(customMessage)
                .build();

        // 发送带 AI 分析摘要的通知
        notifierDispatcher.sendAlarmMessage(alarmDTO);

        log.info("\n===== AI 拒绝分析报告 [{}] =====\n" +
                        "健康状态: {}\n" +
                        "摘要: {}\n" +
                        "调整建议: {}\n" +
                        "详细分析: \n{}\n" +
                        "============================",
                event.getThreadPoolId(), healthStatus, analysisSummary,
                adjustInfo, report.getAnalysis());
    }

    private ThreadPoolExecutorProperties getPoolConfig(String threadPoolId) {
        BootstrapConfigProperties props = BootstrapConfigProperties.getInstance();
        if (props != null && props.getExecutors() != null) {
            return props.getExecutors().stream()
                    .filter(p -> threadPoolId.equals(p.getThreadPoolId()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
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

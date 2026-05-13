package com.baomihuahua.anticipa.agent.scheduled;

import com.baomihuahua.anticipa.agent.AgentLoop;
import com.baomihuahua.anticipa.agent.AgentReport;
import com.baomihuahua.anticipa.agent.SilentRequest;
import com.baomihuahua.anticipa.agent.config.AgentProperties;
import com.baomihuahua.anticipa.agent.discovery.ThreadPoolQueryService;
import com.baomihuahua.anticipa.core.notification.dto.ThreadPoolAlarmNotifyDTO;
import com.baomihuahua.anticipa.core.notification.service.DingTalkMessageService;
import com.baomihuahua.anticipa.core.notification.service.NotifierDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 定时任务执行编排服务。
 * <p>
 * 负责单次定时任务的完整执行流程：
 * 1. 通过 ThreadPoolQueryService 获取远程/本地运行日志和指标
 * 2. 调用 AgentLoop 静默分析
 * 3. 根据策略执行后续操作（通知 / 自动调整）
 * 4. 记录执行日志
 * </p>
 */
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    private static final int DEFAULT_WINDOW_MINUTES = 30;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgentLoop agentLoop;
    private final ThreadPoolQueryService queryService;
    private final ScheduledTaskService taskService;
    private final NotifierDispatcher notifierDispatcher;
    private final AgentProperties agentProperties;

    public TaskExecutionService(AgentLoop agentLoop, ThreadPoolQueryService queryService,
                                 ScheduledTaskService taskService,
                                 NotifierDispatcher notifierDispatcher,
                                 AgentProperties agentProperties) {
        this.agentLoop = agentLoop;
        this.queryService = queryService;
        this.taskService = taskService;
        this.notifierDispatcher = notifierDispatcher;
        this.agentProperties = agentProperties;
    }

    /**
     * 执行一次定时任务。
     *
     * @param task 待执行的定时任务
     * @return 执行日志
     */
    public TaskExecutionLog execute(ScheduledTask task) {
        TaskExecutionLog execLog = new TaskExecutionLog();
        execLog.setTaskId(task.getTaskId());
        execLog.setTaskName(task.getTaskName());
        execLog.setThreadPoolId(task.getThreadPoolId());
        execLog.setAction(task.getAction().name());
        execLog.setStartTime(LocalDateTime.now());

        long startMs = System.currentTimeMillis();

        try {
            // 1. 构建静默请求
            SilentRequest silentRequest = buildSilentRequest(task);

            // 2. 调用 Agent 分析
            AgentReport report = agentLoop.executeSilent(silentRequest);

            // 3. 处理分析结果
            execLog.setAnalysisReport(report.getAnalysis());
            execLog.setResult("SUCCESS");

            // 4. 如果策略为 AUTO_ADJUST 且 AI 建议调整，则调整已由 AgentLoop 执行
            if (task.getAction() == ScheduledTask.TaskAction.AUTO_ADJUST) {
                if (report.isAdjustmentApplied()) {
                    execLog.setAdjusted(true);
                    execLog.setAdjustDetail(report.getAdjustmentResult());
                    log.info("[TaskExecution] task {} auto-adjusted: {}", task.getTaskId(), report.getAdjustmentResult());
                }
            }

            // 5. 如果策略需要通知（NOTIFY_ONLY 或 AUTO_ADJUST），发送钉钉通知
            if (task.getAction() == ScheduledTask.TaskAction.NOTIFY_ONLY
                    || task.getAction() == ScheduledTask.TaskAction.AUTO_ADJUST) {
                try {
                    boolean dispatched = sendTaskNotification(task, report);
                    execLog.setNotified(dispatched);
                    execLog.setNotifyResult(dispatched
                            ? "通知已提交发送"
                            : "通知未发出：无钉钉等 NotifierService，或本分钟内同类型通知已限流（见 NotifierDispatcher 日志）");
                } catch (Exception notifyEx) {
                    log.error("[TaskExecution] task {} notify failed", task.getTaskId(), notifyEx);
                    execLog.setNotified(false);
                    execLog.setNotifyResult("通知发送失败: " + notifyEx.getMessage());
                }
            }

            // 6. 更新上次执行时间（不修改 status，避免覆盖 toggle 设置的 DISABLED）
            task.setLastExecTime(LocalDateTime.now());

            log.info("[TaskExecution] task {} completed successfully", task.getTaskId());

        } catch (Exception e) {
            log.error("[TaskExecution] task {} failed", task.getTaskId(), e);
            execLog.setResult("FAILED");
            execLog.setErrorMessage(e.getMessage());
        }

        execLog.setEndTime(LocalDateTime.now());
        execLog.setDurationMs(System.currentTimeMillis() - startMs);

        // 8. 保存执行日志
        taskService.addExecutionLog(execLog);

        return execLog;
    }

    /**
     * 构建静默请求。
     * <p>
     * 通过 ThreadPoolQueryService 获取远程运行日志和当前指标，
     * 渲染为文本后设置到 SilentRequest。
     * </p>
     */
    private SilentRequest buildSilentRequest(ScheduledTask task) {
        SilentRequest request = new SilentRequest();
        request.setThreadPoolId(task.getThreadPoolId());
        request.setInstanceId(task.getInstanceId());
        request.setAutoAdjust(task.getAction() == ScheduledTask.TaskAction.AUTO_ADJUST);

        // 查询历史运行日志
        request.setLogSummaryText(fetchLogSummary(task));

        // 查询当前运行指标
        request.setConfigText(fetchConfigText(task));

        // 业务场景描述
        if (task.getDescription() != null && !task.getDescription().isBlank()) {
            request.setBusinessDescription(task.getDescription());
        }

        return request;
    }

    /**
     * 通过 ThreadPoolQueryService 直调模式获取历史日志摘要。
     */
    @SuppressWarnings("unchecked")
    private String fetchLogSummary(ScheduledTask task) {
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            String endTime = LocalDateTime.now().format(fmt);
            String startTime = LocalDateTime.now().minus(Duration.ofMinutes(DEFAULT_WINDOW_MINUTES)).format(fmt);

            int limit = Math.max(0, agentProperties.getSilentTaskHistoryLimit());

            Map<String, Object> result = queryService.queryHistoryDirect(
                    task.getThreadPoolId(), task.getInstanceId(),
                    startTime, endTime, "RAW", limit);

            if (result == null) {
                return "无历史运行日志数据";
            }

            int recordCount = result.get("recordCount") instanceof Number
                    ? ((Number) result.get("recordCount")).intValue() : 0;

            if (recordCount == 0) {
                return "无历史运行日志数据";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("共 ").append(recordCount).append(" 条记录\n");

            Object recordsObj = result.get("records");
            if (recordsObj instanceof List) {
                List<?> records = (List<?>) recordsObj;
                int step = Math.max(1, records.size() / 20);
                for (int i = 0; i < records.size(); i += step) {
                    Object item = records.get(i);
                    if (item instanceof Map) {
                        Map<String, Object> r = (Map<String, Object>) item;
                        appendRecordLine(sb, r);
                    }
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("[TaskExecution] failed to fetch log summary for task {}", task.getTaskId(), e);
            return "获取历史日志失败: " + e.getMessage();
        }
    }

    /**
     * 通过 ThreadPoolQueryService 直调模式获取当前配置/指标文本。
     */
    @SuppressWarnings("unchecked")
    private String fetchConfigText(ScheduledTask task) {
        try {
            Map<String, Object> metrics = queryService.queryPoolMetricsDirect(
                    task.getThreadPoolId(), task.getInstanceId());

            if (metrics == null) {
                return "无法获取当前运行指标";
            }

            StringBuilder sb = new StringBuilder();
            appendConfigLine(sb, "核心线程数", metrics.get("corePoolSize"));
            appendConfigLine(sb, "最大线程数", metrics.get("maximumPoolSize"));
            appendConfigLine(sb, "队列容量", metrics.get("queueCapacity"));
            appendConfigLine(sb, "活跃线程数", metrics.get("activeCount"));
            appendConfigLine(sb, "当前队列大小", metrics.get("queueSize"));
            appendConfigLine(sb, "拒绝次数", metrics.get("rejectCount"));

            // 多实例聚合场景
            Object instancesObj = metrics.get("instances");
            if (instancesObj instanceof List) {
                List<?> instances = (List<?>) instancesObj;
                sb.append("\n实例数: ").append(instances.size()).append("\n");
                for (Object inst : instances) {
                    if (inst instanceof Map) {
                        Map<String, Object> instMap = (Map<String, Object>) inst;
                        Object instId = instMap.get("instanceId");
                        if (instId != null) {
                            sb.append("  实例 ").append(instId).append(": ");
                            sb.append("core=").append(instMap.getOrDefault("corePoolSize", "-"));
                            sb.append(", max=").append(instMap.getOrDefault("maximumPoolSize", "-"));
                            sb.append(", active=").append(instMap.getOrDefault("activeCount", "-"));
                            sb.append(", queue=").append(instMap.getOrDefault("queueSize", "-"));
                            sb.append("/").append(instMap.getOrDefault("queueCapacity", "-"));
                            sb.append("\n");
                        }
                    }
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("[TaskExecution] failed to fetch config for task {}", task.getTaskId(), e);
            return "获取当前指标失败: " + e.getMessage();
        }
    }

    private void appendRecordLine(StringBuilder sb, Map<String, Object> r) {
        Object timestamp = r.get("timestamp");
        Object queueCapacity = r.get("queueCapacity");
        int capacity = queueCapacity instanceof Number ? ((Number) queueCapacity).intValue() : 0;
        int queueSize = r.get("queueSize") instanceof Number ? ((Number) r.get("queueSize")).intValue() : 0;
        String usageStr;
        if (capacity > 0) {
            int usage = queueSize * 100 / capacity;
            usageStr = queueSize + " | " + capacity + " | " + usage + "%";
        } else {
            usageStr = queueSize + " | " + capacity + " | -";
        }
        sb.append("| ").append(timestamp != null ? timestamp : "-")
                .append(" | ").append(r.getOrDefault("activeCount", "-"))
                .append(" | ").append(r.getOrDefault("poolSize", "-"))
                .append(" | ").append(usageStr)
                .append(" | ").append(r.getOrDefault("rejectCount", 0))
                .append(" |\n");
    }

    private void appendConfigLine(StringBuilder sb, String label, Object value) {
        if (value != null) {
            sb.append("- ").append(label).append(": ").append(value).append("\n");
        }
    }

    /**
     * 发送定时任务执行结果通知。
     * <p>
     * 若指定了 notifyWebhookUrl 则直连该 Webhook；否则走系统 {@link NotifierDispatcher}。
     * </p>
     *
     * @return 是否已进入实际发送流程（系统路径下 false 表示无 NotifierService 或触发限流）
     */
    private boolean sendTaskNotification(ScheduledTask task, AgentReport report) {
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
        String actionLabel = task.getAction() == ScheduledTask.TaskAction.AUTO_ADJUST ? "定时分析+自动调整" : "定时分析通知";

        String customMessage = String.format("""
                **<font color=#1E90FF>[定时任务] </font>%s - %s报告**
                
                 ---
                
                <font color='#708090' size=2>任务名称：%s</font>\s
                
                <font color='#708090' size=2>线程池ID：%s</font>\s
                
                <font color='#708090' size=2>目标实例：%s</font>\s
                
                 ---
                
                %s **健康状态：%s** %s
                
                **摘要：%s**
                
                **调整建议：%s**
                
                %s
                
                 ---
                
                **执行时间：%s**
                """,
                task.getThreadPoolId(),
                actionLabel,
                task.getTaskName() != null ? task.getTaskName() : task.getTaskId(),
                task.getThreadPoolId(),
                task.getInstanceId() != null ? task.getInstanceId() : hostAddress,
                healthEmoji, healthStatus, healthEmoji,
                analysisSummary,
                adjustInfo,
                report.getAnalysis() != null ? "> " + report.getAnalysis().replace("\n", "\n> ") : "",
                LocalDateTime.now().format(TIME_FMT)
        );

        ThreadPoolAlarmNotifyDTO alarmDTO = ThreadPoolAlarmNotifyDTO.builder()
                .alarmType("SCHEDULED_TASK_ANALYSIS")
                .threadPoolId(task.getThreadPoolId())
                .applicationName(detectApplicationName())
                .activeProfile(detectActiveProfile())
                .identify(task.getInstanceId() != null ? task.getInstanceId() : hostAddress)
                .currentTime(LocalDateTime.now().format(TIME_FMT))
                .receives("定时任务")
                .interval(1)
                .customMessage(customMessage)
                .build();

        // 如果任务指定了自定义 Webhook，直接发送到该地址
        if (task.getNotifyWebhookUrl() != null && !task.getNotifyWebhookUrl().isBlank()) {
            log.info("[TaskExecution] task {} sending notification via custom webhook", task.getTaskId());
            DingTalkMessageService customService = new DingTalkMessageService(task.getNotifyWebhookUrl());
            customService.sendAlarmMessage(alarmDTO);
            return true;
        }
        log.info("[TaskExecution] task {} sending notification via system NotifierDispatcher", task.getTaskId());
        return notifierDispatcher.sendAlarmMessage(alarmDTO);
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

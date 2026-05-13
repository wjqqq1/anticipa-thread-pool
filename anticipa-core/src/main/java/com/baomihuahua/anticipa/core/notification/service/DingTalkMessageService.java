package com.baomihuahua.anticipa.core.notification.service;

import cn.hutool.core.util.StrUtil;
import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import com.baomihuahua.anticipa.core.config.RejectedAnalysisConfig;
import com.baomihuahua.anticipa.core.constant.Constants;
import com.baomihuahua.anticipa.core.notification.dto.ThreadPoolAlarmNotifyDTO;
import com.baomihuahua.anticipa.core.notification.dto.ThreadPoolConfigChangeDTO;
import com.baomihuahua.anticipa.core.notification.dto.WebThreadPoolConfigChangeDTO;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * 钉钉通知服务
 * <p>
 * 通过钉钉机器人 WebHook 发送报警和配置变更通知。
 * 支持加签（签名）安全模式，配置 secret 后自动启用。
 * </p>
 * <p>
 * Spring 容器中的实例在每次发送前解析 Webhook 与 Secret，可与 Nacos 推送
 * （Anticipa 监听器合并 + Spring Cloud 配置刷新）联动实现热更新。
 * 固定 URL 构造方式 {@link #DingTalkMessageService(String, String)} 仍用于任务级自定义 Webhook。
 * </p>
 * <p>
 * Webhook 解析顺序：{@code anticipa.rejected-analysis.ding-talk.webhook-url} 非空优先，
 * 否则回退 {@code anticipa.notify-platforms.url}。Secret 仅来自 {@code rejected-analysis.ding-talk.secret}。
 * </p>
 */
@Slf4j
public class DingTalkMessageService implements NotifierService {

    private final boolean dynamic;

    private final RejectedAnalysisConfig rejectedAnalysisConfig;
    private final BootstrapConfigProperties bootstrapConfigProperties;

    private final String fixedWebhookUrl;
    private final String fixedSecret;

    /**
     * 固定 Webhook（例如定时任务自定义地址），不参与配置热更新。
     */
    public DingTalkMessageService(String webhookUrl) {
        this(webhookUrl, null);
    }

    /**
     * 固定 Webhook + Secret，不参与配置热更新。
     */
    public DingTalkMessageService(String webhookUrl, String secret) {
        this.dynamic = false;
        this.rejectedAnalysisConfig = null;
        this.bootstrapConfigProperties = null;
        this.fixedWebhookUrl = webhookUrl;
        this.fixedSecret = secret;
    }

    /**
     * 从运行中的配置 Bean 解析 Webhook，支持热更新。
     */
    public DingTalkMessageService(RejectedAnalysisConfig rejectedAnalysisConfig,
                                  BootstrapConfigProperties bootstrapConfigProperties) {
        this.dynamic = true;
        this.rejectedAnalysisConfig = Objects.requireNonNull(rejectedAnalysisConfig, "rejectedAnalysisConfig");
        this.bootstrapConfigProperties = Objects.requireNonNull(bootstrapConfigProperties, "bootstrapConfigProperties");
        this.fixedWebhookUrl = null;
        this.fixedSecret = null;
    }

    @Override
    public void sendAlarmMessage(ThreadPoolAlarmNotifyDTO alarmDTO) {
        // 优先使用自定义消息（如 AI 分析报告）
        if (alarmDTO.getCustomMessage() != null && !alarmDTO.getCustomMessage().isBlank()) {
            sendDingTalkMessage(alarmDTO.getCustomMessage());
            return;
        }

        String message = String.format(Constants.DING_ALARM_NOTIFY_MESSAGE_TEXT,
                alarmDTO.getApplicationName(),
                alarmDTO.getThreadPoolId(),
                alarmDTO.getIdentify(),
                alarmDTO.getAlarmType(),
                alarmDTO.getCorePoolSize(),
                alarmDTO.getMaximumPoolSize(),
                alarmDTO.getCurrentPoolSize(),
                alarmDTO.getActivePoolSize(),
                alarmDTO.getLargestPoolSize(),
                alarmDTO.getCompletedTaskCount(),
                alarmDTO.getWorkQueueName(),
                alarmDTO.getWorkQueueCapacity(),
                alarmDTO.getWorkQueueSize(),
                alarmDTO.getWorkQueueRemainingCapacity(),
                alarmDTO.getRejectedHandlerName(),
                alarmDTO.getRejectCount(),
                alarmDTO.getReceives(),
                alarmDTO.getInterval(),
                alarmDTO.getCurrentTime()
        );

        sendDingTalkMessage(message);
    }

    @Override
    public void sendChangeMessage(ThreadPoolConfigChangeDTO changeDTO) {
        String message = String.format(Constants.DING_CONFIG_CHANGE_MESSAGE_TEXT,
                nullToDash(changeDTO.getApplicationName()),
                nullToDash(changeDTO.getThreadPoolId()),
                nullToDash(changeDTO.getIdentify()),
                nullToDash(changeDTO.getCorePoolSize()),
                nullToDash(changeDTO.getMaximumPoolSize()),
                nullToDash(changeDTO.getKeepAliveTime()),
                nullToDash(changeDTO.getQueueType()),
                nullToDash(changeDTO.getQueueCapacity()),
                nullToDash(changeDTO.getOldRejectedType()),
                nullToDash(changeDTO.getRejectedType()),
                nullToDash(changeDTO.getReceives()),
                nullToDash(changeDTO.getChangeTime())
        );

        sendDingTalkMessage(message);
    }

    @Override
    public void sendWebChangeMessage(WebThreadPoolConfigChangeDTO changeDTO) {
        String message = String.format(Constants.DING_CONFIG_WEB_CHANGE_MESSAGE_TEXT,
                nullToDash(changeDTO.getApplicationName()),
                "Web",
                nullToDash(changeDTO.getIdentify()),
                nullToDash(changeDTO.getCorePoolSize()),
                nullToDash(changeDTO.getMaximumPoolSize()),
                nullToDash(changeDTO.getKeepAliveTime()),
                nullToDash(changeDTO.getReceives()),
                "Web",
                nullToDash(changeDTO.getChangeTime())
        );

        sendDingTalkMessage(message);
    }

    /**
     * 将 null 转为 "-"，避免 String.format 将 null 输出为字符串 "null"
     */
    private String nullToDash(Object value) {
        return value != null ? value.toString() : "-";
    }

    private String resolveWebhookUrl() {
        if (!dynamic) {
            return fixedWebhookUrl;
        }
        RejectedAnalysisConfig.DingTalkConfig ding = rejectedAnalysisConfig.getDingTalk();
        if (ding != null && StrUtil.isNotBlank(ding.getWebhookUrl())) {
            return StrUtil.trim(ding.getWebhookUrl());
        }
        BootstrapConfigProperties.NotifyPlatformsConfig notify = bootstrapConfigProperties.getNotifyPlatforms();
        if (notify != null && StrUtil.isNotBlank(notify.getUrl())) {
            return StrUtil.trim(notify.getUrl());
        }
        return null;
    }

    private String resolveSecret() {
        if (!dynamic) {
            return fixedSecret;
        }
        RejectedAnalysisConfig.DingTalkConfig ding = rejectedAnalysisConfig.getDingTalk();
        if (ding == null || StrUtil.isBlank(ding.getSecret())) {
            return null;
        }
        return StrUtil.trim(ding.getSecret());
    }

    /**
     * 发送钉钉消息
     *
     * @param message 消息内容
     */
    private void sendDingTalkMessage(String message) {
        String webhookUrl = resolveWebhookUrl();
        if (StrUtil.isBlank(webhookUrl)) {
            log.warn("DingTalk webhook URL is not configured, skip sending message");
            return;
        }
        try {
            String url = buildSignedUrl(webhookUrl, resolveSecret());
            String jsonBody = buildMarkdownMessage(message);
            String response = cn.hutool.http.HttpUtil.post(url, jsonBody);
            log.info("DingTalk message sent, response: {}", response);
        } catch (Exception e) {
            log.error("Failed to send DingTalk message (dynamic={})", dynamic, e);
        }
    }

    /**
     * 构建带签名的 WebHook URL（如果配置了 secret）。
     * <p>
     * 钉钉加签模式：timestamp + "\n" + secret → HMAC-SHA256 → Base64 → URL Encode → 拼接为 webhook&timestamp=...&sign=...
     * </p>
     */
    private String buildSignedUrl(String webhookUrl, String secret) throws Exception {
        if (StrUtil.isBlank(secret)) {
            return webhookUrl;
        }
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
        return webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
    }

    /**
     * 构建钉钉 Markdown 消息体
     *
     * @param markdownContent Markdown 内容
     * @return JSON 字符串
     */
    private String buildMarkdownMessage(String markdownContent) {
        return "{\"msgtype\":\"markdown\",\"markdown\":{\"title\":\"动态线程池通知\",\"text\":\"" + escapeJson(markdownContent) + "\"}}";
    }

    /**
     * 转义 JSON 特殊字符
     *
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

package com.baomihuahua.anticipa.core.notification.service;

import cn.hutool.core.date.DateUtil;
import com.baomihuahua.anticipa.core.constant.Constants;
import com.baomihuahua.anticipa.core.notification.dto.ThreadPoolAlarmNotifyDTO;
import com.baomihuahua.anticipa.core.notification.dto.ThreadPoolConfigChangeDTO;
import com.baomihuahua.anticipa.core.notification.dto.WebThreadPoolConfigChangeDTO;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 钉钉通知服务
 * <p>
 * 通过钉钉机器人 WebHook 发送报警和配置变更通知
 * </p>
 */
@Slf4j
public class DingTalkMessageService implements NotifierService {

    /**
     * 钉钉机器人 WebHook 地址
     */
    private final String webhookUrl;

    public DingTalkMessageService(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void sendAlarmMessage(ThreadPoolAlarmNotifyDTO alarmDTO) {
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
                changeDTO.getApplicationName(),
                changeDTO.getThreadPoolId(),
                changeDTO.getIdentify(),
                changeDTO.getCorePoolSize(),
                changeDTO.getMaximumPoolSize(),
                changeDTO.getKeepAliveTime(),
                changeDTO.getQueueType(),
                changeDTO.getQueueCapacity(),
                changeDTO.getOldRejectedType(),
                changeDTO.getRejectedType(),
                changeDTO.getReceives(),
                changeDTO.getChangeTime()
        );

        sendDingTalkMessage(message);
    }

    @Override
    public void sendWebChangeMessage(WebThreadPoolConfigChangeDTO changeDTO) {
        String message = String.format(Constants.DING_CONFIG_WEB_CHANGE_MESSAGE_TEXT,
                changeDTO.getApplicationName(),
                "Web",
                changeDTO.getIdentify(),
                changeDTO.getCorePoolSize(),
                changeDTO.getMaximumPoolSize(),
                changeDTO.getKeepAliveTime(),
                changeDTO.getReceives(),
                "Web",
                changeDTO.getChangeTime()
        );

        sendDingTalkMessage(message);
    }

    /**
     * 发送钉钉消息
     *
     * @param message 消息内容
     */
    private void sendDingTalkMessage(String message) {
        try {
            // 使用 Hutool 的 HttpUtil 发送钉钉消息（需要在 pom.xml 中添加 Hutool 依赖）
            // String response = HttpUtil.post(webhookUrl, buildMarkdownMessage(message));
            // log.info("DingTalk message sent successfully: {}", response);

            // 当前仅打印日志，实际发送需要添加 HTTP 依赖实现
            log.info("DingTalk webhook: {}", webhookUrl);
            log.info("DingTalk message: \n{}", message);
        } catch (Exception e) {
            log.error("Failed to send DingTalk message", e);
        }
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

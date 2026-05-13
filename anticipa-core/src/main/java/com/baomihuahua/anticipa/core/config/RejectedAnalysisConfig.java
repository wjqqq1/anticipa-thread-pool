package com.baomihuahua.anticipa.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 拒绝分析配置。
 * <p>
 * 配置 AI 拒绝分析处理器所需的参数，包括钉钉通知等。
 * 对应配置前缀：{@code anticipa.rejected-analysis}
 * </p>
 */
@ConfigurationProperties(prefix = "anticipa.rejected-analysis")
public class RejectedAnalysisConfig {

    /** 是否启用拒绝分析处理器 */
    private boolean enabled = true;

    /** 拒绝分析的策略名称（用于 {@code RejectedPolicyTypeEnum} 匹配） */
    private String policyName = "AiAnalysisRejectedPolicy";

    /** 钉钉通知配置 */
    private DingTalkConfig dingTalk = new DingTalkConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public DingTalkConfig getDingTalk() {
        return dingTalk;
    }

    public void setDingTalk(DingTalkConfig dingTalk) {
        this.dingTalk = dingTalk;
    }

    /**
     * 钉钉通知配置。
     */
    public static class DingTalkConfig {

        /** 是否启用钉钉通知 */
        private boolean enabled = true;

        /** 钉钉机器人 WebHook 地址 */
        private String webhookUrl;

        /** 钉钉签名密钥（可选，用于加签模式） */
        private String secret;

        /** 通知标题 */
        private String title = "🚨 线程池拒绝告警";

        /** 通知开关：仅在启用时发送钉钉拒绝告警 */
        private boolean notifyOnReject = true;

        /** 通知接收人（钉钉 @ 人，如 "13800000000"），多个用逗号分隔 */
        private String receives;

        /** 告警间隔（分钟），同一线程池在此时间内不重复告警 */
        private int interval = 1;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public boolean isNotifyOnReject() {
            return notifyOnReject;
        }

        public void setNotifyOnReject(boolean notifyOnReject) {
            this.notifyOnReject = notifyOnReject;
        }

        public String getReceives() {
            return receives;
        }

        public void setReceives(String receives) {
            this.receives = receives;
        }

        public int getInterval() {
            return interval;
        }

        public void setInterval(int interval) {
            this.interval = interval;
        }
    }
}

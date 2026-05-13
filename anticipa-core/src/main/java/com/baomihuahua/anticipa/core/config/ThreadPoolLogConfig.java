package com.baomihuahua.anticipa.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 线程池运行日志配置。
 * <p>
 * 控制日志记录的开关、频率、保留策略和 AI 查询窗口。
 * </p>
 */
@ConfigurationProperties(prefix = "anticipa.log")
public class ThreadPoolLogConfig {

    /** 是否启用运行日志持久化 */
    private boolean enabled = false;

    /** 记录频率（秒），默认 10 秒 */
    private long recordIntervalSeconds = 10L;

    /** 日志保留天数，超期自动清理，默认 7 天 */
    private int retentionDays = 7;

    /** 是否压缩旧日志（.gz），默认 true */
    private boolean compress = true;

    /** 日志存储根目录，默认 data/logs */
    private String storePath = "data/logs";

    /** AI 查询日志的配置 */
    private QueryConfig query = new QueryConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getRecordIntervalSeconds() {
        return recordIntervalSeconds;
    }

    public void setRecordIntervalSeconds(long recordIntervalSeconds) {
        this.recordIntervalSeconds = recordIntervalSeconds;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public boolean isCompress() {
        return compress;
    }

    public void setCompress(boolean compress) {
        this.compress = compress;
    }

    public String getStorePath() {
        return storePath;
    }

    public void setStorePath(String storePath) {
        this.storePath = storePath;
    }

    public QueryConfig getQuery() {
        return query;
    }

    public void setQuery(QueryConfig query) {
        this.query = query;
    }

    public static class QueryConfig {

        /** AI 单次查询的最大时间窗口（分钟），默认 60 分钟 */
        private long maxWindowMinutes = 60L;

        /** AI 默认查询的时间窗口（分钟），默认 30 分钟 */
        private long defaultWindowMinutes = 30L;

        public long getMaxWindowMinutes() {
            return maxWindowMinutes;
        }

        public void setMaxWindowMinutes(long maxWindowMinutes) {
            this.maxWindowMinutes = maxWindowMinutes;
        }

        public long getDefaultWindowMinutes() {
            return defaultWindowMinutes;
        }

        public void setDefaultWindowMinutes(long defaultWindowMinutes) {
            this.defaultWindowMinutes = defaultWindowMinutes;
        }
    }
}

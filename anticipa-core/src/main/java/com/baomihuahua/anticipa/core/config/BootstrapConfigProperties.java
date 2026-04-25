package com.baomihuahua.anticipa.core.config;

import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorProperties;
import com.baomihuahua.anticipa.core.parser.ConfigFileTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 配置中心参数
 */
@Data
public class BootstrapConfigProperties {

    public static final String PREFIX = "onethread";

    /**
     * 是否开启动态线程池开关
     */
    private Boolean enable = Boolean.TRUE;

    /**
     * Nacos 配置文件
     */
    private NacosConfig nacos;

    /**
     * Apollo 配置文件
     */
    private ApolloConfig apollo;

    /**
     * Web 线程池配置
     */
    private WebThreadPoolExecutorConfig web;

    /**
     * Nacos 远程配置文件格式类型
     */
    private ConfigFileTypeEnum configFileType;

    /**
     * 通知配置
     */
    private NotifyPlatformsConfig notifyPlatforms;

    /**
     * 监控配置
     */
    private MonitorConfig monitor = new MonitorConfig();

    /**
     * 线程池配置集合
     */
    private List<ThreadPoolExecutorProperties> executors;

    @Data
    public static class NotifyPlatformsConfig {

        /**
         * 通知类型，比如：DING
         */
        private String platform;

        /**
         * 完整 WebHook 地址
         */
        private String url;
    }

    @Data
    public static class MonitorConfig {

        /**
         * 默认开启监控配置
         */
        private Boolean enable = Boolean.TRUE;

        /**
         * 监控类型
         */
        private String collectType = "micrometer";

        /**
         * 采集间隔，默认 10 秒
         */
        private Long collectInterval = 10L;
    }

    @Data
    public static class NacosConfig {

        private String dataId;

        private String group;
    }

    @Data
    public static class ApolloConfig {

        private String namespace;
    }

    @Data
    public static class WebThreadPoolExecutorConfig {

        /**
         * 核心线程数
         */
        private Integer corePoolSize;

        /**
         * 最大线程数
         */
        private Integer maximumPoolSize;

        /**
         * 线程空闲存活时间（单位：秒）
         */
        private Long keepAliveTime;

        /**
         * 通知配置
         */
        private NotifyConfig notify;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotifyConfig {

        /**
         * 接收人集合
         */
        private String receives;
    }

    private static BootstrapConfigProperties INSTANCE = new BootstrapConfigProperties();

    public static BootstrapConfigProperties getInstance() {
        return INSTANCE;
    }

    public static void setInstance(BootstrapConfigProperties properties) {
        INSTANCE = properties;
    }
}

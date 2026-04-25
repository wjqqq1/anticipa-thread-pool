package com.baomihuahua.anticipa.core.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * Web 线程池配置变更通知 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class WebThreadPoolConfigChangeDTO {

    /**
     * 应用名称
     */
    private String applicationName;

    /**
     * 应用标识
     */
    private String identify;

    /**
     * 活跃环境
     */
    private String activeProfile;

    /**
     * 核心线程数
     */
    private Integer corePoolSize;

    /**
     * 最大线程数
     */
    private Integer maximumPoolSize;

    /**
     * 线程存活时间
     */
    private Long keepAliveTime;

    /**
     * 队列类型
     */
    private String queueType;

    /**
     * 队列容量
     */
    private Integer queueCapacity;

    /**
     * 接收人
     */
    private String receives;

    /**
     * 变更时间
     */
    private String changeTime;

    /**
     * 更新时间
     */
    private String updateTime;

    /**
     * 变更的配置项
     */
    private Map<String, ChangePair<?>> changes;

    /**
     * Web 容器名称
     */
    private String webContainerName;

    /**
     * 配置变更对
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Accessors(chain = true)
    public static class ChangePair<T> {

        /**
         * 旧值
         */
        private T oldValue;

        /**
         * 新值
         */
        private T newValue;
    }
}

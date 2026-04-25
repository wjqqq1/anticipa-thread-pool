package com.baomihuahua.anticipa.dashboard.dev.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadPoolDetailRespDTO {

    private String namespace;

    private String serviceName;

    private String dataId;

    private String group;

    private Integer instanceCount;

    private String threadPoolId;

    private Integer corePoolSize;

    private Integer maximumPoolSize;

    private Integer keepAliveTime;

    private Integer queueCapacity;

    private String workQueue;

    private String rejectedHandler;

    private Boolean allowCoreThreadTimeOut;

    private NotifyConfig notify;

    private AlarmConfig alarm;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotifyConfig {

        private List<String> notifyPlatforms;

        private Integer interval;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlarmConfig {

        private Integer capacityAlarmThreshold;

        private Integer activeAlarmThreshold;
    }
}

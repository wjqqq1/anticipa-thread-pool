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
public class WebThreadPoolDetailRespDTO {

    private String namespace;

    private String serviceName;

    private String dataId;

    private String group;

    private Integer instanceCount;

    private String webContainerName;

    private Integer corePoolSize;

    private Integer maximumPoolSize;

    private Integer keepAliveTime;

    private NotifyConfig notify;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotifyConfig {

        private List<String> notifyPlatforms;

        private Integer interval;
    }
}

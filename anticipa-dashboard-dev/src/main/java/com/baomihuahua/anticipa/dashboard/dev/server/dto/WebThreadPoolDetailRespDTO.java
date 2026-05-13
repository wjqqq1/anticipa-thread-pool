package com.baomihuahua.anticipa.dashboard.dev.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebThreadPoolDetailRespDTO {

    private String namespace;

    private String serviceName;

    private String dataId;

    private String group;

    private String webContainerName;

    private Integer corePoolSize;

    private Integer maximumPoolSize;

    private Long keepAliveTime;

    private Integer instanceCount;

    private NotifyConfig notify;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotifyConfig {

        private String receives;
    }
}

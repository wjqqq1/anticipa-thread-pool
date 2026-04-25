package com.baomihuahua.anticipa.dashboard.dev.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebThreadPoolBaseMetricsRespDTO {

    private String ip;

    private Integer port;

    private String webContainerName;

    private Integer corePoolSize;

    private Integer maximumPoolSize;

    private Integer activeCount;

    private Integer poolSize;

    private Long completedTaskCount;

    private Long taskCount;

    private Integer largestPoolSize;

    private Long keepAliveTime;
}

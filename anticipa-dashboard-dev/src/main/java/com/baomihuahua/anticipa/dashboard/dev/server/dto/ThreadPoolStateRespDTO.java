package com.baomihuahua.anticipa.dashboard.dev.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadPoolStateRespDTO {

    private String threadPoolId;

    private Integer corePoolSize;

    private Integer maximumPoolSize;

    private Integer activeCount;

    private Integer poolSize;

    private Integer queueSize;

    private Integer queueRemainingCapacity;

    private Long completedTaskCount;

    private Long taskCount;

    private Long largestPoolSize;

    private String queueType;

    private String rejectedExecutionHandler;

    private Long keepAliveTime;

    private Boolean allowCoreThreadTimeOut;

    private Integer waitTaskCount;
}

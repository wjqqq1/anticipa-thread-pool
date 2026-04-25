package com.baomihuahua.anticipa.dashboard.dev.starter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoolAdjustRequestDTO {
    private String poolId;
    private Integer corePoolSize;
    private Integer maximumPoolSize;
    private Integer queueCapacity;
    private Long keepAliveTime;
    private String workQueue;
    private String rejectedHandler;
    private Boolean allowCoreThreadTimeOut;
    private String source;
    private String reason;
}

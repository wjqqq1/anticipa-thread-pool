package com.baomihuahua.anticipa.dashboard.dev.starter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoolConfigRespDTO {
    private String threadPoolId;
    private Integer corePoolSize;
    private Integer maximumPoolSize;
    private Long keepAliveTime;
    private String queueType;
    private Integer queueCapacity;
    private String rejectedHandler;
    private Boolean allowCoreThreadTimeOut;
}

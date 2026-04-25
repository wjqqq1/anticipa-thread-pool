package com.baomihuahua.anticipa.dashboard.dev.server.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadPoolUpdateReqDTO {

    @NotBlank(message = "命名空间不为空")
    private String namespace;

    @NotBlank(message = "DataId 不为空")
    private String dataId;

    @NotBlank(message = "Group 不为空")
    private String group;

    @NotBlank(message = "线程池 ID 不为空")
    private String threadPoolId;

    private Integer corePoolSize;

    private Integer maximumPoolSize;

    private Integer keepAliveTime;

    private Integer queueCapacity;

    private String workQueue;

    private String rejectedHandler;

    private Boolean allowCoreThreadTimeOut;

    private ThreadPoolDetailRespDTO.NotifyConfig notify;

    private ThreadPoolDetailRespDTO.AlarmConfig alarm;
}

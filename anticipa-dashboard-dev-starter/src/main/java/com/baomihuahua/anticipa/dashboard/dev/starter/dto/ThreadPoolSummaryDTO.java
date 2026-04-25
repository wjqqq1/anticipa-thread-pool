package com.baomihuahua.anticipa.dashboard.dev.starter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadPoolSummaryDTO {
    private String poolId;
    private Integer corePoolSize;
    private Integer maximumPoolSize;
    private Integer activeCount;
    private Integer queueSize;
    private Integer queueCapacity;
    private Long completedTaskCount;
    private Long rejectCount;
}

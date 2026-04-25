package com.baomihuahua.anticipa.dashboard.dev.starter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstanceSnapshotDTO {
    private InstanceInfoRespDTO instanceInfo;
    private Long timestamp;
    private List<ThreadPoolSummaryDTO> threadPools;
}

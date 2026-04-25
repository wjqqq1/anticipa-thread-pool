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
public class SimulateResultDTO {
    private String poolId;
    private AdjustSnapshotDTO currentConfig;
    private AdjustSnapshotDTO simulatedConfig;
    private List<String> expectedEffects;
    private String riskLevel;
    private List<String> riskWarnings;
}

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
public class AdjustResultDTO {
    private String poolId;
    private Boolean success;
    private AdjustSnapshotDTO before;
    private AdjustSnapshotDTO after;
    private List<String> adjustedFields;
    private String message;
    private Long timestamp;
}

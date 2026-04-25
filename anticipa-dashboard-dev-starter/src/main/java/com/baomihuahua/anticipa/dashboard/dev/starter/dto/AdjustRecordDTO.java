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
public class AdjustRecordDTO {
    private String snapshotId;
    private Long timestamp;
    private String source;
    private String reason;
    private AdjustSnapshotDTO before;
    private AdjustSnapshotDTO after;
    private List<String> changedFields;
}

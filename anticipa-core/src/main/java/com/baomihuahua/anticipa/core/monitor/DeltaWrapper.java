package com.baomihuahua.anticipa.core.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 线程池运行时数据包装器，用于计算增量指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeltaWrapper {

    /**
     * 当前拒绝次数
     */
    private Long rejectCount;

    /**
     * 当前完成任务数
     */
    private Long completedTaskCount;
}

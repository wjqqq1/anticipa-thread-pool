package com.baomihuahua.anticipa.core.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 线程池运行时信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ThreadPoolRuntimeInfo {

    /**
     * 线程池唯一标识
     */
    private String threadPoolId;

    /**
     * 应用名称
     */
    private String applicationName;

    /**
     * 核心线程数
     */
    private Integer corePoolSize;

    /**
     * 最大线程数
     */
    private Integer maximumPoolSize;

    /**
     * 当前线程数
     */
    private Integer poolSize;

    /**
     * 活跃线程数
     */
    private Integer activeCount;

    /**
     * 同存最大线程数
     */
    private Integer largestPoolSize;

    /**
     * 完成任务总数
     */
    private Long completedTaskCount;

    /**
     * 队列类型
     */
    private String queueType;

    /**
     * 当前队列大小
     */
    private Integer queueSize;

    /**
     * 队列剩余容量
     */
    private Integer queueRemainingCapacity;

    /**
     * 队列总容量
     */
    private Integer queueCapacity;

    /**
     * 拒绝策略
     */
    private String rejectedExecutionHandler;
}

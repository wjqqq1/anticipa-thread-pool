package com.baomihuahua.anticipa.dashboard.dev.starter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadPoolDashBoardDevRespDTO {

    /**
     * 线程池唯一标识
     */
    private String threadPoolId;

    /**
     * 当前环境
     */
    private String activeProfile;

    /**
     * 运行状态
     */
    private String status;

    /**
     * IP
     */
    private String ip;

    /**
     * 端口
     */
    private String port;

    /**
     * 当前负载
     */
    private String currentLoad;

    /**
     * 峰值负载
     */
    private String peakLoad;

    /**
     * 剩余内存（MB）
     */
    private String freeMemory;

    /**
     * 内存占比
     */
    private String memoryUsagePercentage;

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
    private Integer currentPoolSize;

    /**
     * 活跃线程数
     */
    private Integer activePoolSize;

    /**
     * 最大线程数
     */
    private Integer largestPoolSize;

    /**
     * 线程池任务总量
     */
    private Long completedTaskCount;

    /**
     * 线程空闲存活时间（单位：秒）
     */
    private Long keepAliveTime;

    /**
     * 阻塞队列类型
     */
    private String workQueueName;

    /**
     * 队列容量
     */
    private Integer workQueueCapacity;

    /**
     * 队列元素数量
     */
    private Integer workQueueSize;

    /**
     * 队列剩余容量
     */
    private Integer workQueueRemainingCapacity;

    /**
     * 拒绝策略
     */
    private String rejectedHandlerName;

    /**
     * 执行拒绝策略次数
     */
    private Long rejectCount;

    /**
     * 当前时间
     */
    private String currentTime;
}

package com.baomihuahua.anticipa.core.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.function.Supplier;

/**
 * 线程池报警通知数据模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ThreadPoolAlarmNotifyDTO {

    /**
     * 报警类型：Activity、Capacity、Reject
     */
    private String alarmType;

    /**
     * 线程池唯一标识
     */
    private String threadPoolId;

    /**
     * 应用名称
     */
    private String applicationName;

    /**
     * 环境标识
     */
    private String activeProfile;

    /**
     * 应用标识，例如 IP
     */
    private String identify;

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
     * 同存最大线程数
     */
    private Integer largestPoolSize;

    /**
     * 线程池任务总量
     */
    private Long completedTaskCount;

    /**
     * 队列名称
     */
    private String workQueueName;

    /**
     * 队列元素个数
     */
    private Integer workQueueSize;

    /**
     * 队列剩余个数
     */
    private Integer workQueueRemainingCapacity;

    /**
     * 队列容量
     */
    private Integer workQueueCapacity;

    /**
     * 拒绝策略名称
     */
    private String rejectedHandlerName;

    /**
     * 拒绝次数
     */
    private Long rejectCount;

    /**
     * 当前时间
     */
    private String currentTime;

    /**
     * 接收人
     */
    private String receives;

    /**
     * 告警间隔
     */
    private Integer interval;

    /**
     * 延迟加载数据提供者
     */
    private Supplier<ThreadPoolAlarmNotifyDTO> supplier;

    /**
     * 设置延迟加载数据提供者
     *
     * @param supplier 数据提供者
     */
    public void setSupplier(Supplier<ThreadPoolAlarmNotifyDTO> supplier) {
        this.supplier = supplier;
    }
}

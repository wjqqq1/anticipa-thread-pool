package com.baomihuahua.anticipa.web.starter.core.executor;

import com.baomihuahua.anticipa.web.starter.core.WebContainerEnum;
import com.baomihuahua.anticipa.web.starter.core.WebThreadPoolBaseMetrics;
import com.baomihuahua.anticipa.web.starter.core.WebThreadPoolConfig;
import com.baomihuahua.anticipa.web.starter.core.WebThreadPoolState;

/**
 * 用于管理和监控 Web 容器中线程池的通用接口
 * <p>
 * 支持动态更新线程池配置、获取基础与完整运行状态，以及识别当前运行的 Web 容器类型
 * 此接口主要用于 Web 运行时线程池的动态扩缩容、监控采集和性能调优
 */
public interface WebThreadPoolService {

    /**
     * 动态更新线程池的配置，如核心线程数、最大线程数和空闲线程存活时间。
     *
     * @param config 新的线程池配置参数
     */
    void updateThreadPool(WebThreadPoolConfig config);

    /**
     * 获取线程池的轻量级运行指标（无锁，适合高频调用）
     *
     * @return WebThreadPoolState 的简化视图，仅包含关键运行时指标
     */
    WebThreadPoolBaseMetrics getBasicMetrics();

    /**
     * 获取线程池的完整运行时状态（可能涉及锁操作，不建议高频调用）
     *
     * @return 完整的线程池运行状态信息
     */
    WebThreadPoolState getRuntimeState();

    /**
     * 获取线程池运行状态
     *
     * @return 当前线程池运行状态
     */
    String getRunningStatus();

    /**
     * 获取当前应用所使用的 Web 容器类型，如 Tomcat、Jetty 等
     *
     * @return Web 容器类型的枚举值
     */
    WebContainerEnum getWebContainerType();
}

package com.baomihuahua.anticipa.core.executor.support;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 拒绝策略类型枚举
 */
public enum RejectedPolicyTypeEnum {

    /**
     * {@link ThreadPoolExecutor.CallerRunsPolicy}
     */
    CALLER_RUNS_POLICY("CallerRunsPolicy", new ThreadPoolExecutor.CallerRunsPolicy()),

    /**
     * {@link ThreadPoolExecutor.AbortPolicy}
     */
    ABORT_POLICY("AbortPolicy", new ThreadPoolExecutor.AbortPolicy()),

    /**
     * {@link ThreadPoolExecutor.DiscardPolicy}
     */
    DISCARD_POLICY("DiscardPolicy", new ThreadPoolExecutor.DiscardPolicy()),

    /**
     * {@link ThreadPoolExecutor.DiscardOldestPolicy}
     */
    DISCARD_OLDEST_POLICY("DiscardOldestPolicy", new ThreadPoolExecutor.DiscardOldestPolicy()),

    /**
     * AI 分析拒绝策略。
     * <p>
     * 触发时发布 Spring 事件，通知 AI 模块拉取日志并分析。
     * 同时发送钉钉通知（默认行为）。处理器由 Spring 配置动态创建并注册。
     * 注意：此枚举值关联的 handler 为 null，实际处理器通过
     * {@link #registerHandler(String, RejectedExecutionHandler)} 动态注册。
     * </p>
     */
    AI_ANALYSIS("AiAnalysisRejectedPolicy", null);

    @Getter
    private String name;

    @Getter
    private RejectedExecutionHandler rejectedHandler;

    RejectedPolicyTypeEnum(String rejectedPolicyName, RejectedExecutionHandler rejectedHandler) {
        this.name = rejectedPolicyName;
        this.rejectedHandler = rejectedHandler;
    }

    private static final Map<String, RejectedPolicyTypeEnum> NAME_TO_ENUM_MAP;

    /** 动态注册的拒绝处理器（如 AI 分析策略），key 为策略名称 */
    private static final Map<String, RejectedExecutionHandler> DYNAMIC_HANDLERS = new ConcurrentHashMap<>();

    static {
        final RejectedPolicyTypeEnum[] values = RejectedPolicyTypeEnum.values();
        NAME_TO_ENUM_MAP = new HashMap<>(values.length);
        for (RejectedPolicyTypeEnum value : values) {
            NAME_TO_ENUM_MAP.put(value.name, value);
        }
    }

    /**
     * Creates a {@link RejectedExecutionHandler} based on the given
     * {@link RejectedPolicyTypeEnum#name RejectedPolicyTypeEnum.name}.
     * <p>
     * 优先查找枚举中预定义的处理器。如果枚举值存在但 handler 为 null
     * （如 {@link #AI_ANALYSIS}），则从动态注册表中查找。
     * </p>
     *
     * @param rejectedPolicyName the {@link RejectedPolicyTypeEnum#name RejectedPolicyTypeEnum.name}
     * @return the corresponding {@link RejectedExecutionHandler} instance
     * @throws IllegalArgumentException if no matching rejected policy type is found
     */
    public static RejectedExecutionHandler createPolicy(String rejectedPolicyName) {
        RejectedPolicyTypeEnum rejectedPolicyTypeEnum = NAME_TO_ENUM_MAP.get(rejectedPolicyName);
        if (rejectedPolicyTypeEnum != null) {
            // 枚举存在且 handler 不为 null，直接返回
            if (rejectedPolicyTypeEnum.rejectedHandler != null) {
                return rejectedPolicyTypeEnum.rejectedHandler;
            }
            // 枚举存在但 handler 为 null（如 AI_ANALYSIS），从动态注册表查找
            RejectedExecutionHandler dynamicHandler = DYNAMIC_HANDLERS.get(rejectedPolicyName);
            if (dynamicHandler != null) {
                return dynamicHandler;
            }
            throw new IllegalArgumentException(
                    "Dynamic handler not registered: " + rejectedPolicyName
                            + ". Ensure the handler bean is initialized (e.g. AiAnalysisRejectedHandler).");
        }

        throw new IllegalArgumentException("No matching type of rejected execution was found: " + rejectedPolicyName);
    }

    /**
     * 注册一个动态拒绝处理器。
     * <p>
     * 用于注册无法在枚举编译期预创建的处理器（如需要 Spring 注入的
     * {@link com.baomihuahua.anticipa.core.executor.support.AiAnalysisRejectedHandler}）。
     * 注册后可通过 {@link #createPolicy(String)} 按名称获取。
     * </p>
     *
     * @param name    策略名称，需与配置中的 {@code rejectedHandler} 值一致
     * @param handler 拒绝处理器实例
     */
    public static void registerHandler(String name, RejectedExecutionHandler handler) {
        DYNAMIC_HANDLERS.put(name, handler);
    }
}

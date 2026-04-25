package com.baomihuahua.anticipa.core.toolkit;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用于构建自定义线程工厂的建造者类，支持设置线程名、优先级、是否为守护线程等属性
 */
public class ThreadFactoryBuilder {

    /**
     * 基础线程工厂，默认使用 Executors.defaultThreadFactory()
     */
    private ThreadFactory backingThreadFactory;

    /**
     * 线程名前缀，如 "onethread-"，线程名形如：onethread-1
     */
    private String namePrefix;

    /**
     * 是否为守护线程，默认 false
     */
    private Boolean daemon;

    /**
     * 线程优先级（1~10）
     */
    private Integer priority;

    /**
     * 未捕获异常处理器
     */
    private Thread.UncaughtExceptionHandler uncaughtExceptionHandler;

    /**
     * 创建 ThreadFactoryBuilder 实例
     */
    public static ThreadFactoryBuilder builder() {
        return new ThreadFactoryBuilder();
    }

    public ThreadFactoryBuilder threadFactory(ThreadFactory backingThreadFactory) {
        this.backingThreadFactory = backingThreadFactory;
        return this;
    }

    public ThreadFactoryBuilder namePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
        return this;
    }

    public ThreadFactoryBuilder daemon(boolean daemon) {
        this.daemon = daemon;
        return this;
    }

    public ThreadFactoryBuilder priority(int priority) {
        if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY) {
            throw new IllegalArgumentException("The thread priority must be between 1 and 10.");
        }
        this.priority = priority;
        return this;
    }

    public ThreadFactoryBuilder uncaughtExceptionHandler(Thread.UncaughtExceptionHandler handler) {
        this.uncaughtExceptionHandler = handler;
        return this;
    }

    /**
     * 构建线程工厂实例
     */
    public ThreadFactory build() {
        final ThreadFactory factory = (this.backingThreadFactory != null) ? this.backingThreadFactory : Executors.defaultThreadFactory();
        Assert.notEmpty(namePrefix, "The thread name prefix cannot be empty or an empty string.");
        final AtomicLong count = (StrUtil.isNotBlank(namePrefix)) ? new AtomicLong(0) : null;

        return runnable -> {
            Thread thread = factory.newThread(runnable);

            if (count != null) {
                thread.setName(namePrefix + count.getAndIncrement());
            }

            if (daemon != null) {
                thread.setDaemon(daemon);
            }

            if (priority != null) {
                thread.setPriority(priority);
            }

            if (uncaughtExceptionHandler != null) {
                thread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
            }

            return thread;
        };
    }
}

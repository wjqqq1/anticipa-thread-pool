package com.baomihuahua.anticipa.config.common.starter.refresher;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 动态刷新线程池配置并发安全刷新测试
 */
public class ThreadPoolIdLockTest {

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(5);

        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                // 每个线程构造一个独立对象，但 threadPoolId 内容相同
                ThreadPoolExecutorProperties props = new ThreadPoolExecutorProperties("core-biz-pool");

                // ❌ 不加 intern：锁失效
                Object lock = props.getThreadPoolId();

                // ✅ 加 intern：同内容，同锁对象
                // Object lock = props.getThreadPoolId().intern();

                synchronized (lock) {
                    String threadName = Thread.currentThread().getName();
                    System.out.printf("[%d] %s 正在刷新线程池 %s%n",
                            System.currentTimeMillis(), threadName, props.getThreadPoolId());

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    System.out.printf("[%d] %s 刷新完成%n",
                            System.currentTimeMillis(), threadName);
                }
            });
        }

        executor.shutdown();
    }
}

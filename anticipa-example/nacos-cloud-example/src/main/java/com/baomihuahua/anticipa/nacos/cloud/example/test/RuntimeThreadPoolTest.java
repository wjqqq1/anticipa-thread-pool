package com.baomihuahua.anticipa.nacos.cloud.example.test;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeThreadPoolTest {

    private final ThreadPoolExecutor onethreadProducer;
    private final ThreadPoolExecutor onethreadConsumer;
    private final List<ScheduledExecutorService> burstSchedulers = new ArrayList<>();

    private static final int MAX_TASK = Integer.MAX_VALUE;

    // 使用更安全的线程池构造
    private final ExecutorService simulationExecutor = Executors.newFixedThreadPool(
            2,
            new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "simulator-thread-" + count.getAndIncrement());
                }
            }
    );

    @PostConstruct
    public void init() {
        startTestSimulation();

        // 启动定时高压段调度
        schedulePeriodicBurstTask(onethreadProducer, "onethreadProducer");
        schedulePeriodicBurstTask(onethreadConsumer, "onethreadConsumer");
    }

    public void startTestSimulation() {
        simulationExecutor.submit(this::simulateHighActiveThreadUsage);
        simulationExecutor.submit(this::simulateQueueUsageHigh);
    }

    /**
     * 模拟活跃线程数占比高的情况
     */
    @SneakyThrows
    private void simulateHighActiveThreadUsage() {
        for (int i = 0; i < MAX_TASK; i++) {
            sleepRandom(1, 1000);
            try {
                onethreadProducer.execute(() -> sleepRandom(200, 1000));
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 模拟阻塞队列占比高的情况
     */
    @SneakyThrows
    private void simulateQueueUsageHigh() {
        for (int i = 0; i < MAX_TASK; i++) {
            sleepRandom(100, 500);
            try {
                onethreadConsumer.execute(() -> sleepRandom(200, 500));
            } catch (Exception ignored) {
            }
        }
    }


    /**
     * 定期突发任务模拟（每1~2小时启动1分钟的高频提交）
     */
    private void schedulePeriodicBurstTask(ThreadPoolExecutor executor, String name) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-burst-scheduler"));
        burstSchedulers.add(scheduler);

        scheduler.scheduleAtFixedRate(() -> {
                    log.info("[突发模式] 开始 {} 的高频提交模拟", name);
                    long end = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1); // 高压持续1分钟
                    while (System.currentTimeMillis() < end) {
                        try {
                            executor.execute(() -> {
                                try {
                                    TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(10, 100));
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
                            // 高频提交（毫秒级）
                            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(5, 20));
                        } catch (RejectedExecutionException e) {
                            log.warn("[突发模式] 任务被拒绝: {}", name);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    log.info("[突发模式] 结束 {} 的高频提交模拟", name);
                },
                ThreadLocalRandom.current().nextLong(10, 20), // 初次延迟（小时）
                ThreadLocalRandom.current().nextLong(60, 120), // 间隔周期（小时）
                TimeUnit.MINUTES
        );
    }

    private void sleepRandom(int minMillis, int maxMillis) {
        try {
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(minMillis, maxMillis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("模拟线程被中断，当前线程：{}", Thread.currentThread().getName(), e);
        }
    }
}

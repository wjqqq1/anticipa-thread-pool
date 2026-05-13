package com.baomihuahua.anticipa.nacos.cloud.example.configuration;

import com.baomihuahua.anticipa.core.executor.support.BlockingQueueTypeEnum;
import com.baomihuahua.anticipa.core.toolkit.ThreadPoolExecutorBuilder;
import com.baomihuahua.anticipa.spring.base.DynamicThreadPool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class DynamicThreadPoolConfiguration {

    @Bean
    @DynamicThreadPool
    public ThreadPoolExecutor anticipaProducer() {
        return ThreadPoolExecutorBuilder.builder()
                .threadPoolId("anticipa-producer")
                .corePoolSize(2)
                .maximumPoolSize(4)
                .keepAliveTime(9999L)
                .awaitTerminationMillis(5000L)
                .workQueueType(BlockingQueueTypeEnum.SYNCHRONOUS_QUEUE)
                .threadFactory("anticipa-producer_")
                .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())
                .dynamicPool()
                .build();
    }

    @Bean
    @DynamicThreadPool
    public ThreadPoolExecutor anticipaConsumer() {
        return ThreadPoolExecutorBuilder.builder()
                .threadPoolId("anticipa-consumer")
                .corePoolSize(4)
                .maximumPoolSize(6)
                .keepAliveTime(9999L)
                .workQueueType(BlockingQueueTypeEnum.SYNCHRONOUS_QUEUE)
                .threadFactory("anticipa-consumer_")
                .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())
                .dynamicPool()
                .build();
    }

    @Bean
    @DynamicThreadPool
    public ThreadPoolExecutor anticipaOrderHandler() {
        return ThreadPoolExecutorBuilder.builder()
                .threadPoolId("anticipa-order-handler")
                .corePoolSize(4)
                .maximumPoolSize(8)
                .keepAliveTime(60L)
                .workQueueType(BlockingQueueTypeEnum.RESIZABLE_CAPACITY_LINKED_BLOCKING_QUEUE)
                .workQueueCapacity(200)
                .threadFactory("anticipa-order_")
                .rejectedHandler(new ThreadPoolExecutor.AbortPolicy())
                .dynamicPool()
                .build();
    }
}

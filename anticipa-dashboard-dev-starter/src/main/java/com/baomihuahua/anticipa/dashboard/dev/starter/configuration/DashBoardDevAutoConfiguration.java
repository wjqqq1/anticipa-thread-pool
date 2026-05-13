package com.baomihuahua.anticipa.dashboard.dev.starter.configuration;

import com.baomihuahua.anticipa.dashboard.dev.starter.controller.*;
import com.baomihuahua.anticipa.dashboard.dev.starter.service.DynamicThreadPoolOperator;
import com.baomihuahua.anticipa.dashboard.dev.starter.service.DynamicThreadPoolService;
import com.baomihuahua.anticipa.dashboard.dev.starter.service.WebThreadPoolService;
import com.baomihuahua.anticipa.dashboard.dev.starter.store.AdjustHistoryStore;
import com.baomihuahua.anticipa.core.monitor.ThreadPoolLogStore;
import com.baomihuahua.anticipa.core.notification.service.NotifierDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 客户端 Starter 自动配置。
 * <p>
 * 仅在客户端应用中加载（客户端应用引入此 starter 依赖），
 * 负责暴露本机线程池指标的 REST 端点，供控制台通过 HTTP 远程拉取。
 * 控制台应用不依赖此 starter，所以这些 Bean 不会出现在控制台 JVM 中。
 * </p>
 */
@Configuration
public class DashBoardDevAutoConfiguration {

    @Bean
    public DynamicThreadPoolService dynamicThreadPoolService() {
        return new DynamicThreadPoolService();
    }

    @Bean
    public DynamicThreadPoolOperator dynamicThreadPoolOperator(NotifierDispatcher notifierDispatcher) {
        return new DynamicThreadPoolOperator(notifierDispatcher);
    }

    @Bean
    public DynamicThreadPoolController dynamicThreadPoolController(
            DynamicThreadPoolService dynamicThreadPoolService,
            DynamicThreadPoolOperator dynamicThreadPoolOperator) {
        return new DynamicThreadPoolController(dynamicThreadPoolService, dynamicThreadPoolOperator);
    }

    @Bean
    public WebThreadPoolService webThreadPoolService(com.baomihuahua.anticipa.web.starter.core.executor.WebThreadPoolService webThreadPoolService) {
        return new WebThreadPoolService(webThreadPoolService);
    }

    @Bean
    public WebThreadPoolController webThreadPoolController(WebThreadPoolService webThreadPoolService) {
        return new WebThreadPoolController(webThreadPoolService);
    }

    @Bean
    public AdjustHistoryStore adjustHistoryStore() {
        return new AdjustHistoryStore();
    }

    @Bean
    public CapabilityController capabilityController() {
        return new CapabilityController();
    }

    @Bean
    @ConditionalOnMissingClass("com.baomihuahua.anticipa.agent.AgentLoop")
    public AgentFallbackController agentFallbackController() {
        return new AgentFallbackController();
    }

    @Bean
    @ConditionalOnBean(ThreadPoolLogStore.class)
    public ThreadPoolLogController threadPoolLogController(ThreadPoolLogStore logStore) {
        return new ThreadPoolLogController(logStore);
    }
}

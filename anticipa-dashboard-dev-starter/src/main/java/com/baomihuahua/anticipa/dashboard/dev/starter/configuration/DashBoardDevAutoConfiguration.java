package com.baomihuahua.anticipa.dashboard.dev.starter.configuration;

import com.baomihuahua.anticipa.dashboard.dev.starter.controller.DynamicThreadPoolController;
import com.baomihuahua.anticipa.dashboard.dev.starter.controller.WebThreadPoolController;
import com.baomihuahua.anticipa.dashboard.dev.starter.service.DynamicThreadPoolOperator;
import com.baomihuahua.anticipa.dashboard.dev.starter.service.DynamicThreadPoolService;
import com.baomihuahua.anticipa.dashboard.dev.starter.service.WebThreadPoolService;
import com.baomihuahua.anticipa.dashboard.dev.starter.store.AdjustHistoryStore;
import org.springframework.context.annotation.Bean;

public class DashBoardDevAutoConfiguration {

    @Bean
    public DynamicThreadPoolService dynamicThreadPoolService() {
        return new DynamicThreadPoolService();
    }

    @Bean
    public DynamicThreadPoolOperator dynamicThreadPoolOperator() {
        return new DynamicThreadPoolOperator();
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
}

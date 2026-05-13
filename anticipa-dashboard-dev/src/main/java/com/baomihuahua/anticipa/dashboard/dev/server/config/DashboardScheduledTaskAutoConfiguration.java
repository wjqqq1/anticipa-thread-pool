package com.baomihuahua.anticipa.dashboard.dev.server.config;

import com.baomihuahua.anticipa.agent.config.AgentAutoConfiguration;
import com.baomihuahua.anticipa.agent.scheduled.ScheduledTaskService;
import com.baomihuahua.anticipa.agent.scheduled.TaskScheduler;
import com.baomihuahua.anticipa.dashboard.dev.server.controller.ScheduledTaskController;
import com.baomihuahua.anticipa.spring.base.configuration.AnticipaBaseConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 定时任务 Controller 注册（由 {@link com.baomihuahua.anticipa.dashboard.dev.server.AnticipaDashboardApplication} 显式 {@code @Import}）。
 * <p>
 * 不在此类上使用 {@code @ConditionalOnBean(ScheduledTaskService)}：在自动配置阶段求值时，
 * Agent 注册的 {@code ScheduledTaskService} 可能尚未进入 BeanFactory，会导致整类被跳过、接口 404。
 * 改为仅保留 {@code @ConditionalOnProperty}，由 {@code @Bean} 方法参数声明对 {@code ScheduledTaskService} 的依赖，
 * 由 Spring 在创建阶段解析顺序。
 * </p>
 */
@Configuration
@AutoConfigureAfter({AnticipaBaseConfiguration.class, AgentAutoConfiguration.class})
@ConditionalOnProperty(prefix = "anticipa.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DashboardScheduledTaskAutoConfiguration {

    @Bean
    public ScheduledTaskController scheduledTaskController(ScheduledTaskService taskService,
                                                            ObjectProvider<TaskScheduler> taskScheduler) {
        return new ScheduledTaskController(taskService, taskScheduler);
    }
}

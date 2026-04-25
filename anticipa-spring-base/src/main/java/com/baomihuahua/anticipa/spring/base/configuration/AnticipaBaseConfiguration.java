package com.baomihuahua.anticipa.spring.base.configuration;

import com.baomihuahua.anticipa.core.alarm.ThreadPoolAlarmChecker;
import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import com.baomihuahua.anticipa.core.monitor.ThreadPoolMonitor;
import com.baomihuahua.anticipa.core.notification.service.AlarmRateLimiter;
import com.baomihuahua.anticipa.core.notification.service.NotifierDispatcher;
import com.baomihuahua.anticipa.core.notification.service.NotifierService;
import com.baomihuahua.anticipa.spring.base.support.ApplicationContextHolder;
import com.baomihuahua.anticipa.spring.base.support.AnticipaBeanPostProcessor;
import com.baomihuahua.anticipa.spring.base.support.SpringPropertiesLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.util.List;

/**
 * 动态线程池基础 Spring 配置类
 */
@Configuration
public class AnticipaBaseConfiguration {

    @Bean
    public ApplicationContextHolder applicationContextHolder() {
        return new ApplicationContextHolder();
    }

    @Bean
    @DependsOn("applicationContextHolder")
    public AnticipaBeanPostProcessor anticipaBeanPostProcessor(BootstrapConfigProperties properties) {
        return new AnticipaBeanPostProcessor(properties);
    }

    @Bean
    public NotifierDispatcher notifierDispatcher(List<NotifierService> notifierServices, AlarmRateLimiter alarmRateLimiter) {
        return new NotifierDispatcher(notifierServices, alarmRateLimiter);
    }

    @Bean
    public SpringPropertiesLoader springPropertiesLoader() {
        return new SpringPropertiesLoader();
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public ThreadPoolAlarmChecker threadPoolAlarmChecker(NotifierDispatcher notifierDispatcher) {
        return new ThreadPoolAlarmChecker(notifierDispatcher);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public ThreadPoolMonitor threadPoolMonitor() {
        return new ThreadPoolMonitor();
    }
}

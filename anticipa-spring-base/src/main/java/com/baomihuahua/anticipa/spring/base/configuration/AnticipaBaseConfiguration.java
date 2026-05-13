package com.baomihuahua.anticipa.spring.base.configuration;

import com.baomihuahua.anticipa.core.alarm.ThreadPoolAlarmChecker;
import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import com.baomihuahua.anticipa.core.config.RejectedAnalysisConfig;
import com.baomihuahua.anticipa.core.config.ThreadPoolLogConfig;
import com.baomihuahua.anticipa.core.executor.support.AiAnalysisRejectedHandler;
import com.baomihuahua.anticipa.core.executor.support.RejectedPolicyTypeEnum;
import com.baomihuahua.anticipa.core.monitor.ThreadPoolLogStore;
import com.baomihuahua.anticipa.core.monitor.ThreadPoolMetricsBinder;
import com.baomihuahua.anticipa.core.monitor.ThreadPoolMonitor;
import com.baomihuahua.anticipa.core.notification.service.AlarmRateLimiter;
import com.baomihuahua.anticipa.core.notification.service.DingTalkMessageService;
import com.baomihuahua.anticipa.core.notification.service.NotifierDispatcher;
import com.baomihuahua.anticipa.core.notification.service.NotifierService;
import com.baomihuahua.anticipa.spring.base.support.ApplicationContextHolder;
import com.baomihuahua.anticipa.spring.base.support.AnticipaBeanPostProcessor;
import com.baomihuahua.anticipa.spring.base.support.SpringPropertiesLoader;
import com.baomihuahua.anticipa.spring.base.enable.ThreadPoolLogMarkerConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

/**
 * 动态线程池基础 Spring 配置类
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({BootstrapConfigProperties.class, ThreadPoolLogConfig.class, RejectedAnalysisConfig.class})
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
    public AlarmRateLimiter alarmRateLimiter() {
        return new AlarmRateLimiter();
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
    @ConditionalOnMissingBean(ThreadPoolMonitor.class)
    public ThreadPoolMonitor threadPoolMonitor(ThreadPoolLogStore threadPoolLogStore, ThreadPoolLogConfig logConfig, Environment environment) {
        String instanceId = resolveInstanceId(environment);
        return new ThreadPoolMonitor(threadPoolLogStore, logConfig, instanceId);
    }

    @Bean
    @ConditionalOnMissingBean(ThreadPoolLogStore.class)
    public ThreadPoolLogStore threadPoolLogStore(ThreadPoolLogConfig logConfig) {
        return new ThreadPoolLogStore(logConfig);
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public ThreadPoolMetricsBinder threadPoolMetricsBinder(Environment environment, MeterRegistry meterRegistry) {
        String appName = environment.getProperty("spring.application.name", "anticipa");
        ThreadPoolMetricsBinder binder = new ThreadPoolMetricsBinder(appName);
        binder.bindTo(meterRegistry);
        return binder;
    }

    // ========== @ConfigurationProperties 嵌套对象转换器 ==========
    // Nacos Config 可能以 LinkedHashMap 而非扁平属性形式提供嵌套配置，
    // Spring Boot Binder 无法自动转换，需要注册自定义 Converter 兜底。
    // 注意：必须使用具名类实现 Converter 接口，不能使用 Lambda（Lambda 会导致泛型类型擦除）。

    @Bean
    @ConfigurationPropertiesBinding
    public AlarmConfigConverter alarmConfigConverter() {
        return new AlarmConfigConverter();
    }

    @Bean
    @ConfigurationPropertiesBinding
    public NotifyConfigConverter notifyConfigConverter() {
        return new NotifyConfigConverter();
    }

    static class AlarmConfigConverter implements Converter<Map, ThreadPoolExecutorProperties.AlarmConfig> {
        @Override
        public ThreadPoolExecutorProperties.AlarmConfig convert(Map source) {
            ThreadPoolExecutorProperties.AlarmConfig config = new ThreadPoolExecutorProperties.AlarmConfig();
            Object enable = source.get("enable");
            if (enable != null) {
                config.setEnable(Boolean.valueOf(enable.toString()));
            }
            config.setQueueThreshold(toInt(source.get("queueThreshold"), source.get("queue-threshold"), 80));
            config.setActiveThreshold(toInt(source.get("activeThreshold"), source.get("active-threshold"), 80));
            return config;
        }
    }

    static class NotifyConfigConverter implements Converter<Map, ThreadPoolExecutorProperties.NotifyConfig> {
        @Override
        public ThreadPoolExecutorProperties.NotifyConfig convert(Map source) {
            ThreadPoolExecutorProperties.NotifyConfig config = new ThreadPoolExecutorProperties.NotifyConfig();
            Object receives = source.get("receives");
            if (receives != null) {
                config.setReceives(receives.toString());
            }
            Object interval = source.get("interval");
            if (interval != null) {
                config.setInterval(toInt(interval, null, 5));
            }
            return config;
        }
    }

    private static Integer toInt(Object primary, Object fallback, int defaultValue) {
        Object value = primary != null ? primary : fallback;
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return Integer.valueOf(value.toString());
    }

    private static String resolveInstanceId(Environment environment) {
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            String port = environment.getProperty("server.port", "8080");
            return host + ":" + port;
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 钉钉通知服务 ==========

    /**
     * enabled 未出现在 Environment 中时，{@link RejectedAnalysisConfig.DingTalkConfig} 在 Java 里默认为 true，
     * 但仅写 {@code havingValue="true"} 且不带 matchIfMissing 会导致「未配置 enabled」时永远不注册 Bean，
     * NotifierDispatcher 得到空的 NotifierService 列表。
     */
    @Bean
    @ConditionalOnMissingBean(DingTalkMessageService.class)
    @ConditionalOnProperty(prefix = "anticipa.rejected-analysis.ding-talk", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DingTalkMessageService dingTalkMessageService(RejectedAnalysisConfig config,
                                                          BootstrapConfigProperties bootstrapConfigProperties) {
        boolean secretPresentAtStartup = config.getDingTalk() != null && config.getDingTalk().getSecret() != null
                && !config.getDingTalk().getSecret().isBlank();
        // 加签是否在发送时启用由 DingTalkMessageService 每次发送前读取 RejectedAnalysisConfig 决定；
        // Nacos 等配置往往在 ApplicationRunner 注册监听器后才合并进 live Bean，故此处 false 不代表未开加签。
        log.info("[Anticipa] Registering DingTalkMessageService (webhook/secret 发送时解析、可热更新). "
                + "启动这一刻 secret 是否已在内存: {}", secretPresentAtStartup);
        return new DingTalkMessageService(config, bootstrapConfigProperties);
    }

    // ========== AI 拒绝分析处理器 ==========

    @Bean
    @ConditionalOnProperty(prefix = "anticipa.rejected-analysis", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AiAnalysisRejectedHandler aiAnalysisRejectedHandler(
            RejectedAnalysisConfig config,
            NotifierDispatcher notifierDispatcher,
            ApplicationEventPublisher eventPublisher) {
        AiAnalysisRejectedHandler handler = new AiAnalysisRejectedHandler(config);
        if (notifierDispatcher != null) {
            handler.setNotifierDispatcher(notifierDispatcher);
        }
        handler.setEventPublisher(eventPublisher);
        // 注册到 RejectedPolicyTypeEnum，使 createPolicy("AiAnalysisRejectedPolicy") 可查
        RejectedPolicyTypeEnum.registerHandler(config.getPolicyName(), handler);
        log.info("[Anticipa] AiAnalysisRejectedHandler registered with policyName='{}', dingTalk.enabled={}, dingTalk.notifyOnReject={}",
                config.getPolicyName(), config.getDingTalk().isEnabled(), config.getDingTalk().isNotifyOnReject());
        return handler;
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> applicationStartupLogger(Environment environment,
                                                                                AnticipaBeanPostProcessor beanPostProcessor,
                                                                                List<NotifierService> notifierServices) {
        return event -> {
            // 所有 Bean 初始化完成后，自动创建 Nacos 配置中有但本地没有 @Bean 声明的线程池
            try {
                beanPostProcessor.createMissingPoolsFromRemoteConfig();
            } catch (Exception e) {
                log.warn("[Anticipa] Failed to auto-create missing thread pools from remote config", e);
            }

            if (notifierServices.isEmpty()) {
                log.warn("[Anticipa] No NotifierService beans (DingTalk 等未注册). "
                        + "请确认配置前缀为 anticipa，且包含 anticipa.rejected-analysis.ding-talk.webhook-url；"
                        + "enabled 可省略(默认开启)，若显式关闭请设为 false。");
            } else {
                log.info("[Anticipa] NotifierService 已注册: {}",
                        notifierServices.stream().map(s -> s.getClass().getSimpleName()).toList());
            }

            try {
                String port = environment.getProperty("local.server.port");
                String appName = environment.getProperty("spring.application.name", "application");
                String host = environment.getProperty("server.address", "localhost");
                log.info("{} started successfully at http://{}:{}", appName, host, port);
            } catch (Exception e) {
                log.warn("Failed to log application startup info", e);
            }
        };
    }
}

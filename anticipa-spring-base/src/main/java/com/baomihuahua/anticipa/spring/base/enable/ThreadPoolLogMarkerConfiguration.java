package com.baomihuahua.anticipa.spring.base.enable;

import com.baomihuahua.anticipa.core.config.ThreadPoolLogConfig;
import com.baomihuahua.anticipa.core.monitor.ThreadPoolLogStore;
import com.baomihuahua.anticipa.core.monitor.ThreadPoolMonitor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.InetAddress;

/**
 * 线程池日志统计标记配置类。
 * <p>
 * 由 {@link EnableThreadPoolLog} 注解通过 {@code @Import} 导入，
 * 在 Spring 容器中注册 Marker Bean，同时创建日志相关 Bean。
 * </p>
 * <p>
 * 由于 {@link AnticipaBaseConfiguration} 中的同名 Bean 使用了
 * {@code @ConditionalOnMissingBean}，当此处先创建时，那里的会自动跳过，
 * 实现"注解或 YAML 任一开启即生效"的语义。
 * </p>
 */
@Configuration
public class ThreadPoolLogMarkerConfiguration {

    @Bean
    public ThreadPoolLogMarker threadPoolLogMarker() {
        return new ThreadPoolLogMarker();
    }

    @Bean
    public ThreadPoolLogStore threadPoolLogStore(ThreadPoolLogConfig logConfig) {
        return new ThreadPoolLogStore(logConfig);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public ThreadPoolMonitor threadPoolMonitor(ThreadPoolLogStore threadPoolLogStore, ThreadPoolLogConfig logConfig, Environment environment) {
        String instanceId = resolveInstanceId(environment);
        return new ThreadPoolMonitor(threadPoolLogStore, logConfig, instanceId);
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

    /**
     * 标记类，用于 {@code @ConditionalOnBean} 条件装配。
     */
    public static class ThreadPoolLogMarker {
    }
}

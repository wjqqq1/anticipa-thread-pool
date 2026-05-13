package com.baomihuahua.anticipa.config.common.starter.configuration;

import com.baomihuahua.anticipa.config.common.starter.refresher.DynamicThreadPoolRefreshListener;
import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import com.baomihuahua.anticipa.core.notification.service.NotifierDispatcher;
import com.baomihuahua.anticipa.spring.base.configuration.AnticipaBaseConfiguration;
import com.baomihuahua.anticipa.spring.base.enable.MarkerConfiguration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * 基于配置中心的公共自动装配配置
 */
@ConditionalOnBean(MarkerConfiguration.Marker.class)
@Import(AnticipaBaseConfiguration.class)
@AutoConfigureAfter(AnticipaBaseConfiguration.class)
@ConditionalOnProperty(prefix = BootstrapConfigProperties.PREFIX, value = "enable", matchIfMissing = true, havingValue = "true")
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BootstrapConfigProperties bootstrapConfigProperties(Environment environment) {
        BootstrapConfigProperties bootstrapConfigProperties = Binder.get(environment)
                .bind(BootstrapConfigProperties.PREFIX, Bindable.of(BootstrapConfigProperties.class))
                .orElse(new BootstrapConfigProperties());
        BootstrapConfigProperties.setInstance(bootstrapConfigProperties);
        return bootstrapConfigProperties;
    }

    /**
     * 当 BootstrapConfigProperties 由 @EnableConfigurationProperties 创建时，
     * 通过该 InitializingBean 确保静态单例 setInstance() 被调用。
     */
    @Bean
    public InitializingBean bootstrapConfigPropertiesInit(BootstrapConfigProperties properties) {
        return () -> BootstrapConfigProperties.setInstance(properties);
    }

    @Bean
    public DynamicThreadPoolRefreshListener dynamicThreadPoolRefreshListener(NotifierDispatcher notifierDispatcher) {
        return new DynamicThreadPoolRefreshListener(notifierDispatcher);
    }

    @Bean
    public AnticipaBannerHandler anticipaBannerHandler(ObjectProvider<BuildProperties> buildProperties) {
        return new AnticipaBannerHandler(buildProperties.getIfAvailable());
    }
}

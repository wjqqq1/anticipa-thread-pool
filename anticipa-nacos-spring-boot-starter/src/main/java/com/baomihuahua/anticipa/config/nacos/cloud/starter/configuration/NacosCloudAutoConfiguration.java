package com.baomihuahua.anticipa.config.nacos.cloud.starter.configuration;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.baomihuahua.anticipa.config.nacos.cloud.starter.refresher.NacosCloudRefresherHandler;
import com.baomihuahua.anticipa.core.config.BootstrapConfigProperties;
import com.baomihuahua.anticipa.spring.base.enable.MarkerConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Nacos Cloud 版本自动装配
 */
@ConditionalOnBean(MarkerConfiguration.Marker.class)
@ConditionalOnProperty(prefix = BootstrapConfigProperties.PREFIX, value = "enable", matchIfMissing = true, havingValue = "true")
public class NacosCloudAutoConfiguration {

    @Bean
    public NacosCloudRefresherHandler nacosCloudRefresherHandler(NacosConfigManager nacosConfigManager, BootstrapConfigProperties properties) {
        return new NacosCloudRefresherHandler(nacosConfigManager.getConfigService(), properties);
    }
}

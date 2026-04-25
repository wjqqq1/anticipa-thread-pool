package com.baomihuahua.anticipa.dashboard.dev.server.service.handler;

import com.baomihuahua.anticipa.dashboard.dev.server.config.DashBoardConfigProperties;
import com.baomihuahua.anticipa.dashboard.dev.server.config.AnticipaProperties;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.client.NacosProxyClient;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigRespDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThreadPoolConfigRefreshHandler {

    private final AnticipaProperties anticipaProperties;
    private final NacosProxyClient nacosProxyClient;
    private final YamlConfigParser yamlConfigParser;

    public void refreshConfig() {
        List<String> namespaces = anticipaProperties.getNamespaces();
        namespaces.forEach(namespace -> {
            List<NacosConfigRespDTO> configs = nacosProxyClient.listConfig(namespace);
            if (configs == null || configs.isEmpty()) {
                return;
            }
            configs.forEach(config -> {
                try {
                    Map<Object, Object> configInfoMap = yamlConfigParser.doParse(config.getContent());
                    ConfigurationPropertySource sources = new MapConfigurationPropertySource(configInfoMap);
                    Binder binder = new Binder(sources);
                    binder.bind("onethread", Bindable.of(DashBoardConfigProperties.class));
                } catch (Exception e) {
                    log.error("Refresh config failed for dataId: {}, group: {}", config.getDataId(), config.getGroup(), e);
                }
            });
        });
    }
}

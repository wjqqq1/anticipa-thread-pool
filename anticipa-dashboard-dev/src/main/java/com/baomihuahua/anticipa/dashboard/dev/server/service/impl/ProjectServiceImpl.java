package com.baomihuahua.anticipa.dashboard.dev.server.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.config.AnticipaProperties;
import com.baomihuahua.anticipa.dashboard.dev.server.config.DashBoardConfigProperties;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ProjectInfoRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.client.NacosProxyClient;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosServiceRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.service.ProjectService;
import com.baomihuahua.anticipa.dashboard.dev.server.service.handler.YamlConfigParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final AnticipaProperties anticipaProperties;
    private final NacosProxyClient nacosProxyClient;
    private final YamlConfigParser yamlConfigParser;

    @Override
    public List<ProjectInfoRespDTO> listProject() {
        Map<String, List<NacosConfigRespDTO>> projectConfigMap = buildProjectConfigMap();
        return projectConfigMap.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split(":", 2);
                    return buildProjectDetail(parts[0], parts[1], entry.getValue());
                })
                .collect(Collectors.toList());
    }

    @Override
    public PageDTO<ProjectInfoRespDTO> listProjectPage(PageReqDTO pageReq) {
        // 第一步：轻量获取项目列表（不解析 YAML、不调 getService）
        Map<String, List<NacosConfigRespDTO>> projectConfigMap = buildProjectConfigMap();
        List<String> allKeys = new ArrayList<>(projectConfigMap.keySet());
        int total = allKeys.size();

        int offset = pageReq.getOffset();
        int size = pageReq.getSize();
        if (offset >= total) {
            return PageDTO.of(new ArrayList<>(), total);
        }

        // 第二步：分页切片，只对当前页的项目做详细查询
        int toIndex = Math.min(offset + size, total);
        List<ProjectInfoRespDTO> pageRecords = allKeys.subList(offset, toIndex).stream()
                .map(key -> {
                    String[] parts = key.split(":", 2);
                    return buildProjectDetail(parts[0], parts[1], projectConfigMap.get(key));
                })
                .collect(Collectors.toList());

        return PageDTO.of(pageRecords, total);
    }

    /**
     * 轻量级获取项目列表：只按 namespace+appName 分组，不做 YAML 解析和 getService 调用
     */
    private Map<String, List<NacosConfigRespDTO>> buildProjectConfigMap() {
        Map<String, List<NacosConfigRespDTO>> projectConfigMap = new LinkedHashMap<>();

        List<String> namespaces = anticipaProperties.getNamespaces();
        namespaces.forEach(namespace -> {
            List<NacosConfigRespDTO> configs = nacosProxyClient.listConfig(namespace, 1, 10000);
            if (CollUtil.isEmpty(configs)) {
                return;
            }

            configs.stream()
                    .filter(each -> StrUtil.isNotBlank(each.getAppName()))
                    .forEach(config -> {
                        String key = namespace + ":" + config.getAppName();
                        projectConfigMap.computeIfAbsent(key, k -> new ArrayList<>()).add(config);
                    });
        });

        return projectConfigMap;
    }

    /**
     * 构建单个项目的详细信息：解析 YAML + 查询实例列表
     */
    private ProjectInfoRespDTO buildProjectDetail(String namespace, String serviceName, List<NacosConfigRespDTO> configs) {
        ProjectAggregate aggregate = new ProjectAggregate(namespace, serviceName);
        configs.forEach(config -> {
            parseConfigInfo(config, aggregate);
            // 通过详情接口获取 modifyTime 作为更新时间
            fillUpdateTime(namespace, config, aggregate);
        });

        List<NacosServiceRespDTO> instances = nacosProxyClient.listInstances(namespace, serviceName);
        return ProjectInfoRespDTO.builder()
                .namespace(namespace)
                .serviceName(serviceName)
                .threadPoolCount(aggregate.threadPoolCount)
                .instanceCount(instances != null ? instances.size() : 0)
                .hasWebThreadPool(aggregate.hasWebThreadPool)
                .updateTime(aggregate.updateTime > 0 ? aggregate.updateTime : null)
                .build();
    }

    /**
     * 解析单条配置 YAML，提取线程池数量和 web 线程池标识，聚合到 ProjectAggregate
     */
    private void parseConfigInfo(NacosConfigRespDTO config, ProjectAggregate aggregate) {
        try {
            Map<Object, Object> configInfoMap = yamlConfigParser.doParse(config.getContent());
            ConfigurationPropertySource sources = new MapConfigurationPropertySource(configInfoMap);
            Binder binder = new Binder(sources);

            // 兼容 anticipa 和 onethread 两种前缀
            DashBoardConfigProperties props = binder
                    .bind("anticipa", Bindable.of(DashBoardConfigProperties.class))
                    .orElseGet(() -> binder.bind("onethread", Bindable.of(DashBoardConfigProperties.class)).orElse(null));

            if (props != null) {
                if (props.getExecutors() != null) {
                    aggregate.threadPoolCount += props.getExecutors().size();
                }
                if (props.getWeb() != null) {
                    aggregate.hasWebThreadPool = true;
                }
            }
        } catch (Exception e) {
            log.warn("parse config error, dataId: {}, group: {}", config.getDataId(), config.getGroup(), e);
        }
    }

    /**
     * 通过详情接口获取配置的 modifyTime，取最新的作为项目更新时间
     */
    private void fillUpdateTime(String namespace, NacosConfigRespDTO config, ProjectAggregate aggregate) {
        try {
            NacosConfigDetailRespDTO detail = nacosProxyClient.getConfig(namespace, config.getDataId(), config.getGroup());
            if (detail != null && detail.getModifyTime() != null && detail.getModifyTime() > aggregate.updateTime) {
                aggregate.updateTime = detail.getModifyTime();
            }
        } catch (Exception e) {
            log.warn("get config detail error, dataId: {}, group: {}", config.getDataId(), config.getGroup(), e);
        }
    }

    /**
     * 项目聚合中间对象，用于在遍历配置时累加统计
     */
    private static class ProjectAggregate {

        final String namespace;
        final String serviceName;
        int threadPoolCount = 0;
        boolean hasWebThreadPool = false;
        long updateTime = 0;

        ProjectAggregate(String namespace, String serviceName) {
            this.namespace = namespace;
            this.serviceName = serviceName;
        }
    }
}

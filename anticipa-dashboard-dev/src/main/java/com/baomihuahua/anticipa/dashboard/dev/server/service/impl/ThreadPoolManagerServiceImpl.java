package com.baomihuahua.anticipa.dashboard.dev.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.config.DashBoardConfigProperties;
import com.baomihuahua.anticipa.dashboard.dev.server.config.AnticipaProperties;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolListReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolUpdateReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.client.NacosProxyClient;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigListRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosServiceRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.service.ThreadPoolManagerService;
import com.baomihuahua.anticipa.dashboard.dev.server.service.handler.YamlConfigParser;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.stereotype.Service;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadPoolManagerServiceImpl implements ThreadPoolManagerService {

    private final AnticipaProperties anticipaProperties;
    private final NacosProxyClient nacosProxyClient;
    private final YamlConfigParser yamlConfigParser;

    @Override
    public List<ThreadPoolDetailRespDTO> listThreadPool(ThreadPoolListReqDTO requestParam) {
        return buildThreadPoolList(requestParam);
    }

    @Override
    public PageDTO<ThreadPoolDetailRespDTO> listThreadPoolPage(ThreadPoolListReqDTO requestParam, PageReqDTO pageReq) {
        String requestedNamespace = requestParam.getNamespace();
        String requestedServiceName = requestParam.getServiceName();

        // 指定了命名空间时，直接将分页参数下推到 Nacos 配置查询 API
        if (StrUtil.isNotBlank(requestedNamespace)) {
            NacosConfigListRespDTO configResp = nacosProxyClient.listConfigWithTotal(
                    requestedNamespace, pageReq.getPage(), pageReq.getSize(), requestedServiceName);

            List<ThreadPoolDetailRespDTO> results = new ArrayList<>();
            if (CollUtil.isNotEmpty(configResp.getPageItems())) {
                configResp.getPageItems().stream()
                        .filter(each -> StrUtil.isNotBlank(each.getAppName()))
                        .filter(each -> StrUtil.isBlank(requestedServiceName) || Objects.equals(each.getAppName(), requestedServiceName))
                        .map(cfg -> buildThreadPoolDetail(requestedNamespace, cfg))
                        .filter(Objects::nonNull)
                        .flatMap(List::stream)
                        .forEach(results::add);
            }

            return PageDTO.of(results, results.size());
        }

        // 未指定命名空间时，遍历所有命名空间分别查询并汇总
        List<String> namespaces = new ArrayList<>(anticipaProperties.getNamespaces());
        List<ThreadPoolDetailRespDTO> results = new ArrayList<>();

        for (String namespace : namespaces) {
            NacosConfigListRespDTO configResp = nacosProxyClient.listConfigWithTotal(
                    namespace, pageReq.getPage(), pageReq.getSize(), requestedServiceName);

            if (CollUtil.isNotEmpty(configResp.getPageItems())) {
                configResp.getPageItems().stream()
                        .filter(each -> StrUtil.isNotBlank(each.getAppName()))
                        .filter(each -> StrUtil.isBlank(requestedServiceName) || Objects.equals(each.getAppName(), requestedServiceName))
                        .map(cfg -> buildThreadPoolDetail(namespace, cfg))
                        .filter(Objects::nonNull)
                        .flatMap(List::stream)
                        .forEach(results::add);
            }
        }

        return PageDTO.of(results, results.size());
    }

    private List<ThreadPoolDetailRespDTO> buildThreadPoolList(ThreadPoolListReqDTO requestParam) {
        List<String> namespaces = new ArrayList<>(anticipaProperties.getNamespaces());
        String requestedNamespace = requestParam.getNamespace() == null ? "public" : requestParam.getNamespace();
        String requestedServiceName = requestParam.getServiceName();
        if (StrUtil.isNotBlank(requestedNamespace) && namespaces.contains(requestedNamespace)) {
            namespaces = Collections.singletonList(requestedNamespace);
        }

        // 并行拉取各 namespace 的配置，并生成 (namespace, config) 任务对
        List<Map.Entry<String, NacosConfigRespDTO>> tasks = namespaces
                .parallelStream()
                .flatMap(ns -> {
                    List<NacosConfigRespDTO> cfgs = nacosProxyClient.listConfig(ns, 1, 100);
                    if (CollUtil.isEmpty(cfgs)) {
                        return Stream.<Map.Entry<String, NacosConfigRespDTO>>empty();
                    }
                    return cfgs.stream()
                            .filter(each -> StrUtil.isNotBlank(each.getAppName()))
                            .filter(each -> StrUtil.isBlank(requestedServiceName) || Objects.equals(each.getAppName(), requestedServiceName))
                            .map(cfg -> new AbstractMap.SimpleEntry<>(ns, cfg));
                })
                .collect(Collectors.toList());

        // 并行处理任务：解析 YAML -> 绑定配置 -> 查询服务实例数 -> 拼装返回
        return tasks.parallelStream()
                .map(entry -> buildThreadPoolDetail(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * 解析单个 Nacos 配置，返回线程池详情列表
     */
    private List<ThreadPoolDetailRespDTO> buildThreadPoolDetail(String namespace, NacosConfigRespDTO config) {
        try {
            Map<Object, Object> configInfoMap = yamlConfigParser.doParse(config.getContent());
            ConfigurationPropertySource sources = new MapConfigurationPropertySource(configInfoMap);
            Binder binder = new Binder(sources);

            // 先尝试 anticipa 前缀，再尝试 onethread 前缀，最后尝试根层级（兼容无前缀的配置）
            BindResult<DashBoardConfigProperties> bound =
                    binder.bind("anticipa", Bindable.of(DashBoardConfigProperties.class));
            if (!bound.isBound()) {
                bound = binder.bind("onethread", Bindable.of(DashBoardConfigProperties.class));
            }
            if (!bound.isBound()) {
                bound = binder.bind("", Bindable.of(DashBoardConfigProperties.class));
            }
            if (!bound.isBound()) {
                return null;
            }

            DashBoardConfigProperties refresherProperties = bound.get();

            List<NacosServiceRespDTO> instances = nacosProxyClient.listInstances(namespace, config.getAppName());

            refresherProperties.getExecutors().forEach(each -> {
                each.setNamespace(namespace);
                each.setServiceName(config.getAppName());
                each.setDataId(config.getDataId());
                each.setGroup(config.getGroup());
                each.setInstanceCount(instances != null ? instances.size() : 0);
            });

            return refresherProperties.getExecutors();
        } catch (Exception e) {
            log.warn("parse config error, namespace: {}, dataId: {}", namespace, config.getDataId(), e);
            return null;
        }
    }

    @SneakyThrows
    @Override
    public void updateGlobalThreadPool(ThreadPoolUpdateReqDTO requestParam) {
        NacosConfigDetailRespDTO configDetail = nacosProxyClient.getConfig(requestParam.getNamespace(), requestParam.getDataId(), requestParam.getGroup());
        String originalContent = configDetail.getContent();

        Map<Object, Object> configInfoMap = yamlConfigParser.doParse(originalContent);
        ConfigurationPropertySource source = new MapConfigurationPropertySource(configInfoMap);

        Binder binder = new Binder(source);
        DashBoardConfigProperties anticipa = binder.bind("anticipa", Bindable.of(DashBoardConfigProperties.class))
                .orElseGet(() -> binder.bind("onethread", Bindable.of(DashBoardConfigProperties.class))
                        .orElseGet(() -> binder.bind("", Bindable.of(DashBoardConfigProperties.class))
                                .orElseThrow(() -> new RuntimeException("binding failed"))));

        // 查找要更新的线程池，并记录原始值用于对比
        ThreadPoolDetailRespDTO originalPool = null;
        for (ThreadPoolDetailRespDTO e : anticipa.getExecutors()) {
            if (e.getThreadPoolId().equals(requestParam.getThreadPoolId())) {
                originalPool = BeanUtil.toBean(e, ThreadPoolDetailRespDTO.class);
                break;
            }
        }

        if (originalPool == null) {
            throw new RuntimeException("线程池不存在: " + requestParam.getThreadPoolId());
        }

        // 执行更新
        anticipa.getExecutors().stream()
                .filter(e -> e.getThreadPoolId().equals(requestParam.getThreadPoolId()))
                .findFirst()
                .ifPresent(e -> {
                    e.setCorePoolSize(requestParam.getCorePoolSize());
                    e.setMaximumPoolSize(requestParam.getMaximumPoolSize());
                    e.setKeepAliveTime(requestParam.getKeepAliveTime());
                    e.setQueueCapacity(requestParam.getQueueCapacity());
                    e.setWorkQueue(requestParam.getWorkQueue());
                    e.setRejectedHandler(requestParam.getRejectedHandler());
                    e.setAllowCoreThreadTimeOut(requestParam.getAllowCoreThreadTimeOut());
                    e.setNotify(BeanUtil.toBean(requestParam.getNotify(), ThreadPoolDetailRespDTO.NotifyConfig.class));
                    e.setAlarm(BeanUtil.toBean(requestParam.getAlarm(), ThreadPoolDetailRespDTO.AlarmConfig.class));
                });

        YAMLFactory factory = new YAMLFactory();
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);

        ObjectMapper objectMapper = new ObjectMapper(factory);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);

        String yamlStr = objectMapper.writeValueAsString(Collections.singletonMap("anticipa", anticipa));
        nacosProxyClient.publishConfig(requestParam.getNamespace(), requestParam.getDataId(), requestParam.getGroup(), configDetail.getAppName(), configDetail.getId(), configDetail.getMd5(), yamlStr, "yaml");

        printConfigChangeLog(originalPool, requestParam);
    }

    private void printConfigChangeLog(ThreadPoolDetailRespDTO original, ThreadPoolUpdateReqDTO updated) {
        String threadPoolId = updated.getThreadPoolId();
        String delimiter = " => ";

        StringBuilder logMessage = new StringBuilder();
        logMessage.append("\n===========================================\n");
        logMessage.append("[").append(threadPoolId).append("] Dynamic thread pool parameter changed:\n");

        appendChange(logMessage, "corePoolSize", original.getCorePoolSize(), updated.getCorePoolSize(), delimiter);
        appendChange(logMessage, "maximumPoolSize", original.getMaximumPoolSize(), updated.getMaximumPoolSize(), delimiter);
        appendChange(logMessage, "queueCapacity", original.getQueueCapacity(), updated.getQueueCapacity(), delimiter);
        appendChange(logMessage, "keepAliveTime", original.getKeepAliveTime(), updated.getKeepAliveTime(), delimiter);
        appendChange(logMessage, "rejectedHandler", original.getRejectedHandler(), updated.getRejectedHandler(), delimiter);
        appendChange(logMessage, "allowCoreThreadTimeOut", original.getAllowCoreThreadTimeOut(), updated.getAllowCoreThreadTimeOut(), delimiter);

        logMessage.append("===========================================");

        log.info(logMessage.toString());
    }

    private <T> void appendChange(StringBuilder sb, String fieldName, T before, T after, String delimiter) {
        if (!Objects.equals(before, after)) {
            sb.append(fieldName).append(": ")
              .append(before).append(delimiter).append(after)
              .append("\n");
        }
    }
}

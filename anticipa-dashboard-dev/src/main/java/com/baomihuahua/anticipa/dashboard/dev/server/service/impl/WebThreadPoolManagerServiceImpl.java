package com.baomihuahua.anticipa.dashboard.dev.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.baomihuahua.anticipa.dashboard.dev.server.config.DashBoardConfigProperties;
import com.baomihuahua.anticipa.dashboard.dev.server.config.AnticipaProperties;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolListReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolStateRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolUpdateReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.client.NacosProxyClient;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigListRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosServiceListRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosServiceRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.service.WebThreadPoolManagerService;
import com.baomihuahua.anticipa.dashboard.dev.server.service.handler.YamlConfigParser;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WebThreadPoolManagerServiceImpl implements WebThreadPoolManagerService {

    private final AnticipaProperties anticipaProperties;
    private final NacosProxyClient nacosProxyClient;
    private final YamlConfigParser yamlConfigParser;

    @Override
    public List<WebThreadPoolDetailRespDTO> listThreadPool(WebThreadPoolListReqDTO requestParam) {
        return buildWebThreadPoolList(requestParam);
    }

    @Override
    public PageDTO<WebThreadPoolDetailRespDTO> listThreadPoolPage(WebThreadPoolListReqDTO requestParam, PageReqDTO pageReq) {
        String requestedNamespace = requestParam.getNamespace();
        String requestedServiceName = requestParam.getServiceName();

        // 如果指定了 namespace，直接使用 Nacos 分页查询
        if (StrUtil.isNotBlank(requestedNamespace) && anticipaProperties.getNamespaces().contains(requestedNamespace)) {
            NacosConfigListRespDTO nacosPage = nacosProxyClient.listConfigWithTotal(requestedNamespace, pageReq.getPage(), pageReq.getSize());
            if (nacosPage == null || CollUtil.isEmpty(nacosPage.getPageItems())) {
                return PageDTO.of(new ArrayList<>(), 0);
            }

            List<WebThreadPoolDetailRespDTO> records = nacosPage.getPageItems().stream()
                    .filter(each -> StrUtil.isNotBlank(each.getAppName()))
                    .filter(each -> StrUtil.isBlank(requestedServiceName) || Objects.equals(each.getAppName(), requestedServiceName))
                    .map(config -> buildWebThreadPoolDetail(requestedNamespace, config))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            return PageDTO.of(records, nacosPage.getTotalCount());
        }

        // 未指定 namespace：全量拉取后手动分页
        List<WebThreadPoolDetailRespDTO> all = buildWebThreadPoolList(requestParam);
        int offset = pageReq.getOffset();
        int size = pageReq.getSize();
        int total = all.size();

        if (offset >= total) {
            return PageDTO.of(new ArrayList<>(), total);
        }

        int toIndex = Math.min(offset + size, total);
        return PageDTO.of(all.subList(offset, toIndex), total);
    }

    private List<WebThreadPoolDetailRespDTO> buildWebThreadPoolList(WebThreadPoolListReqDTO requestParam) {
        List<String> namespaces = new ArrayList<>(anticipaProperties.getNamespaces());
        String requestedNamespace = requestParam.getNamespace();
        String requestedServiceName = requestParam.getServiceName();
        if (StrUtil.isNotBlank(requestedNamespace) && namespaces.contains(requestedNamespace)) {
            namespaces.clear();
            namespaces.add(requestedNamespace);
        }

        List<WebThreadPoolDetailRespDTO> results = new ArrayList<>();
        namespaces.forEach(namespace -> {
            List<NacosConfigRespDTO> nacosConfigResponse = nacosProxyClient.listConfig(namespace);
            if (CollUtil.isNotEmpty(nacosConfigResponse)) {
                nacosConfigResponse.stream()
                        .filter(each -> {
                            if (StrUtil.isBlank(each.getAppName())) return false;
                            return StrUtil.isBlank(requestedServiceName) || Objects.equals(each.getAppName(), requestedServiceName);
                        })
                        .forEach(config -> {
                            WebThreadPoolDetailRespDTO detail = buildWebThreadPoolDetail(namespace, config);
                            if (detail != null) {
                                results.add(detail);
                            }
                        });
            }
        });

        return results;
    }

    /**
     * 解析单个 Nacos 配置，返回 Web 线程池详情
     */
    private WebThreadPoolDetailRespDTO buildWebThreadPoolDetail(String namespace, NacosConfigRespDTO config) {
        try {
            Map<Object, Object> configInfoMap = yamlConfigParser.doParse(config.getContent());
            ConfigurationPropertySource sources = new MapConfigurationPropertySource(configInfoMap);
            Binder binder = new Binder(sources);

            DashBoardConfigProperties refresherProperties;
            try {
                refresherProperties = binder
                        .bind("anticipa", Bindable.of(DashBoardConfigProperties.class))
                        .orElseThrow(() -> new IllegalArgumentException("anticipa config binding failed"));
            } catch (Exception e) {
                return null;
            }

            NacosServiceListRespDTO service = nacosProxyClient.getService(namespace, config.getAppName());
            DashBoardConfigProperties.WebThreadPoolExecutorConfig webThreadPoolConfig = refresherProperties.getWeb();
            if (service == null || CollUtil.isEmpty(service.getServiceList()) || webThreadPoolConfig == null) {
                return null;
            }

            NacosServiceRespDTO nacosService = service.getServiceList().get(0);
            String networkAddress = nacosService.getIp() + ":" + nacosService.getPort();

            Result<WebThreadPoolStateRespDTO> result;
            try {
                String resultStr = HttpUtil.get("http://" + networkAddress + "/web/thread-pool", 1000);
                result = JSON.parseObject(resultStr, new TypeReference<>() {});
            } catch (Exception e) {
                return null;
            }
            String webContainerName = result.getData().getWebContainerName();

            return WebThreadPoolDetailRespDTO.builder()
                    .webContainerName(webContainerName)
                    .namespace(namespace)
                    .serviceName(config.getAppName())
                    .dataId(config.getDataId())
                    .group(config.getGroup())
                    .instanceCount(service.getCount())
                    .corePoolSize(webThreadPoolConfig.getCorePoolSize())
                    .maximumPoolSize(webThreadPoolConfig.getMaximumPoolSize())
                    .keepAliveTime(webThreadPoolConfig.getKeepAliveTime())
                    .notify(BeanUtil.toBean(webThreadPoolConfig.getNotify(), WebThreadPoolDetailRespDTO.NotifyConfig.class))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    @SneakyThrows
    @Override
    public void updateGlobalThreadPool(WebThreadPoolUpdateReqDTO requestParam) {
        NacosConfigDetailRespDTO configDetail = nacosProxyClient.getConfig(requestParam.getNamespace(), requestParam.getDataId(), requestParam.getGroup());
        String originalContent = configDetail.getContent();

        Map<Object, Object> configInfoMap = yamlConfigParser.doParse(originalContent);
        ConfigurationPropertySource source = new MapConfigurationPropertySource(configInfoMap);

        Binder binder = new Binder(source);
        DashBoardConfigProperties anticipa = binder.bind("anticipa", Bindable.of(DashBoardConfigProperties.class))
                .orElseThrow(() -> new RuntimeException("binding failed"));

        anticipa.setWeb(BeanUtil.toBean(requestParam, DashBoardConfigProperties.WebThreadPoolExecutorConfig.class));

        Map<String, Object> updatedMap = new LinkedHashMap<>();
        updatedMap.put("anticipa", anticipa);

        YAMLFactory factory = new YAMLFactory();
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);

        ObjectMapper objectMapper = new ObjectMapper(factory);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);

        String yamlStr = objectMapper.writeValueAsString(Collections.singletonMap("anticipa", anticipa));
        nacosProxyClient.publishConfig(requestParam.getNamespace(), requestParam.getDataId(), requestParam.getGroup(), configDetail.getAppName(), configDetail.getId(), configDetail.getMd5(), yamlStr, "yaml");
    }
}

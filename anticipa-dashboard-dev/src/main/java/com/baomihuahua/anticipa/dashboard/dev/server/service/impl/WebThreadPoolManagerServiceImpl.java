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
import com.baomihuahua.anticipa.dashboard.dev.server.config.AnticipaProperties;
import com.baomihuahua.anticipa.dashboard.dev.server.config.DashBoardConfigProperties;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolListReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolStateRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolUpdateReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.client.NacosProxyClient;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigListRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigRespDTO;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        // 未传入 namespace 时默认查询 public 命名空间
        String requestedNamespace = StrUtil.isBlank(requestParam.getNamespace()) ? "public" : requestParam.getNamespace();
        String requestedServiceName = requestParam.getServiceName();

        NacosConfigListRespDTO configResp = nacosProxyClient.listConfigWithTotal(
                requestedNamespace, pageReq.getPage(), pageReq.getSize(), requestedServiceName);

        List<WebThreadPoolDetailRespDTO> results = new ArrayList<>();
        if (CollUtil.isNotEmpty(configResp.getPageItems())) {
            configResp.getPageItems().stream()
                    .filter(each -> StrUtil.isNotBlank(each.getAppName()))
                    .filter(each -> StrUtil.isBlank(requestedServiceName) || Objects.equals(each.getAppName(), requestedServiceName))
                    .forEach(config -> {
                        WebThreadPoolDetailRespDTO detail = buildWebThreadPoolDetail(requestedNamespace, config);
                        if (detail != null) {
                            results.add(detail);
                        }
                    });
        }

        return PageDTO.of(results, results.size());
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
            List<NacosConfigRespDTO> nacosConfigResponse = nacosProxyClient.listConfig(namespace, 1, 100);
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

            // 先尝试 anticipa 前缀，再尝试 onethread 前缀，最后尝试根层级（兼容无前缀的配置）
            org.springframework.boot.context.properties.bind.BindResult<DashBoardConfigProperties> bound =
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

            DashBoardConfigProperties.WebThreadPoolExecutorConfig webThreadPoolConfig = refresherProperties.getWeb();
            if (webThreadPoolConfig == null) {
                return null;
            }

            List<NacosServiceRespDTO> instances = nacosProxyClient.listInstances(namespace, config.getAppName());
            if (instances == null || instances.isEmpty()) {
                return null;
            }

            NacosServiceRespDTO nacosService = instances.get(0);
            String networkAddress = nacosService.getIp() + ":" + nacosService.getPort();

            String webContainerName = null;
            try {
                String resultStr = HttpUtil.get("http://" + networkAddress + "/api/anticipa-dashboard/web/thread-pool", 1000);
                Result<WebThreadPoolStateRespDTO> result = JSON.parseObject(resultStr, new TypeReference<>() {});
                if (result != null && result.getData() != null) {
                    webContainerName = result.getData().getWebContainerName();
                }
            } catch (Exception e) {
                // HTTP 调用失败时仍返回配置信息，仅 webContainerName 为空
                System.out.println("HTTP 调用失败: " + e.getMessage());
            }

            return WebThreadPoolDetailRespDTO.builder()
                    .webContainerName(webContainerName)
                    .namespace(namespace)
                    .serviceName(config.getAppName())
                    .dataId(config.getDataId())
                    .group(config.getGroup())
                    .instanceCount(instances.size())
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
                .orElseGet(() -> binder.bind("onethread", Bindable.of(DashBoardConfigProperties.class))
                        .orElseGet(() -> binder.bind("", Bindable.of(DashBoardConfigProperties.class))
                                .orElseThrow(() -> new RuntimeException("binding failed"))));

        anticipa.setWeb(BeanUtil.toBean(requestParam, DashBoardConfigProperties.WebThreadPoolExecutorConfig.class));

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

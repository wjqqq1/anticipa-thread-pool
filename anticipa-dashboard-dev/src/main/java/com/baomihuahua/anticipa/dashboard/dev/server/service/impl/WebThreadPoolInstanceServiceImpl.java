package com.baomihuahua.anticipa.dashboard.dev.server.service.impl;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolBaseMetricsRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolStateRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.client.NacosProxyClient;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosServiceListRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.service.WebThreadPoolInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebThreadPoolInstanceServiceImpl implements WebThreadPoolInstanceService {

    private final NacosProxyClient nacosProxyClient;

    private final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 10,
            Runtime.getRuntime().availableProcessors() * 10,
            60,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    @Override
    public List<WebThreadPoolBaseMetricsRespDTO> listBasicMetrics(String namespace, String serviceName) {
        NacosServiceListRespDTO serviceListResponse = nacosProxyClient.getService(namespace, serviceName);
        if (serviceListResponse == null || serviceListResponse.getCount() == 0) {
            return List.of();
        }

        List<CompletableFuture<WebThreadPoolBaseMetricsRespDTO>> futures = serviceListResponse.getServiceList().stream()
                .map(service -> CompletableFuture.supplyAsync(() -> {
                    try {
                        // 调用 DashBoard Starter 提供的内置接口，获取线程池运行信息
                        String networkAddress = service.getIp() + ":" + service.getPort();
                        String resultStr = HttpUtil.get("http://" + networkAddress + "/web/thread-pool/basic-metrics");
                        Result<WebThreadPoolBaseMetricsRespDTO> result = JSON.parseObject(resultStr, new TypeReference<>() {
                        });
                        return result.getData();
                    } catch (Exception e) {
                        log.error("Error fetching metrics from {}", service.getIp(), e);
                        return null;
                    }
                }, threadPoolExecutor))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public WebThreadPoolStateRespDTO getRuntimeState(String networkAddress) {
        String resultStr = HttpUtil.get("http://" + networkAddress + "/web/thread-pool");
        Result<WebThreadPoolStateRespDTO> result = JSON.parseObject(resultStr, new TypeReference<>() {
        });
        return result.getData();
    }
}

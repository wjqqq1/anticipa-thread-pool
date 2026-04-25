package com.baomihuahua.anticipa.dashboard.dev.starter.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.net.Ipv4Util;
import com.baomihuahua.anticipa.dashboard.dev.starter.dto.AdjustResultDTO;
import com.baomihuahua.anticipa.dashboard.dev.starter.dto.AdjustSnapshotDTO;
import com.baomihuahua.anticipa.dashboard.dev.starter.dto.InstanceInfoRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.starter.dto.PoolAdjustRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

/**
 * Web 线程池运行时调整服务
 */
@Slf4j
public class WebThreadPoolOperator {

    @Value("${server.port:8080}")
    private String port;

    @Value("${spring.application.name:unknown}")
    private String appName;

    @Value("${spring.profiles.active:UNKNOWN}")
    private String activeProfile;

    public InstanceInfoRespDTO getInstanceInfo() {
        return InstanceInfoRespDTO.builder()
                .instanceId(appName + ":" + getLocalIp() + ":" + port)
                .appName(appName)
                .host(getLocalIp())
                .port(port)
                .activeProfile(activeProfile.toUpperCase())
                .startTime(DateUtil.now())
                .sdkVersion("1.0.0")
                .build();
    }

    /**
     * 获取 Web 线程池快照（简化版）
     */
    public AdjustSnapshotDTO captureSnapshot() {
        // Web 容器线程池目前仅提供基础指标，简化快照返回
        return AdjustSnapshotDTO.builder()
                .corePoolSize(0)
                .maximumPoolSize(0)
                .activeCount(0)
                .queueSize(0)
                .queueCapacity(0)
                .completedTaskCount(0L)
                .rejectCount(-1L)
                .coreUsageRate(0.0)
                .queueUsageRate(0.0)
                .build();
    }

    /**
     * 调整 Web 线程池（暂不支持，保留扩展）
     */
    public AdjustResultDTO adjust(PoolAdjustRequestDTO request) {
        log.warn("[WEB-ADJUST] Web 容器线程池调整暂不支持, poolId={}", request.getPoolId());
        return AdjustResultDTO.builder()
                .poolId(request.getPoolId())
                .success(false)
                .message("Web 容器线程池调整暂不支持")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private String getLocalIp() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}

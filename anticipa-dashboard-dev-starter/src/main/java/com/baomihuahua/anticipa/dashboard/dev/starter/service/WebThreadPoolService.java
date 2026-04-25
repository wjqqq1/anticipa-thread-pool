package com.baomihuahua.anticipa.dashboard.dev.starter.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.net.Ipv4Util;
import com.baomihuahua.anticipa.dashboard.dev.starter.dto.WebThreadPoolDashBoardDevRespDTO;
import com.baomihuahua.anticipa.web.starter.core.WebThreadPoolBaseMetrics;
import com.baomihuahua.anticipa.web.starter.core.WebThreadPoolState;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import static com.baomihuahua.anticipa.dashboard.dev.starter.toolkit.MemoryUtil.getFreeMemory;
import static com.baomihuahua.anticipa.dashboard.dev.starter.toolkit.MemoryUtil.getMemoryProportion;

@RequiredArgsConstructor
public class WebThreadPoolService {

    private final com.baomihuahua.anticipa.web.starter.core.executor.WebThreadPoolService webThreadPoolService;

    @Value("${server.port:8080}")
    private String port;

    @Value("${spring.profiles.active:UNKNOWN}")
    private String activeProfile;

    /**
     * 获取线程池的轻量级运行指标（无锁，适合高频调用）
     *
     * @return WebThreadPoolState 的简化视图，仅包含关键运行时指标
     */
    public WebThreadPoolBaseMetrics getBasicMetrics() {
        WebThreadPoolBaseMetrics basicMetrics = webThreadPoolService.getBasicMetrics();
        basicMetrics.setActiveProfile(activeProfile.toUpperCase());
        basicMetrics.setNetworkAddress(Ipv4Util.LOCAL_IP + ":" + port);
        basicMetrics.setWebContainerName(webThreadPoolService.getWebContainerType().getName());
        return basicMetrics;
    }

    /**
     * 获取线程池的完整运行时状态（可能涉及锁操作，不建议高频调用）
     *
     * @return 完整的线程池运行状态信息
     */
    public WebThreadPoolDashBoardDevRespDTO getRuntimeInfo() {
        WebThreadPoolState runtimeState = webThreadPoolService.getRuntimeState();
        WebThreadPoolDashBoardDevRespDTO responseDTO = BeanUtil.toBean(runtimeState, WebThreadPoolDashBoardDevRespDTO.class);
        responseDTO.setCurrentTime(DateUtil.now())
                .setActiveProfile(activeProfile.toUpperCase())
                .setIp(Ipv4Util.LOCAL_IP)
                .setWebContainerName(webThreadPoolService.getWebContainerType().getName())
                .setPort(port)
                .setCurrentLoad((int) Math.round((runtimeState.getActivePoolSize() * 100.0) / runtimeState.getMaximumPoolSize()) + "%")
                .setPeakLoad((int) Math.round((runtimeState.getLargestPoolSize() * 100.0) / runtimeState.getMaximumPoolSize()) + "%")
                .setFreeMemory(getFreeMemory())
                .setMemoryUsagePercentage(getMemoryProportion())
                .setStatus(webThreadPoolService.getRunningStatus());
        return responseDTO;
    }
}

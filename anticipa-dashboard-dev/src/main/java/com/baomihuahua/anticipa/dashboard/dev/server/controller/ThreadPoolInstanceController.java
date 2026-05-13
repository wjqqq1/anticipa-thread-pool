package com.baomihuahua.anticipa.dashboard.dev.server.controller;

import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolBaseMetricsRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolStateRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.service.ThreadPoolInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 线程池实例控制层。
 * <p>
 * 控制台不持有本地线程池数据，通过 Nacos 发现客户端实例，
 * 再 HTTP 调用客户端 Starter 暴露的端点获取线程池指标。
 * </p>
 */
@RestController
@RequiredArgsConstructor
public class ThreadPoolInstanceController {

    private final ThreadPoolInstanceService threadPoolInstanceService;

    /**
     * 获取动态线程池列表指标
     */
    @GetMapping("/api/anticipa-dashboard/thread-pools/{namespace}/{serviceName}/{threadPoolId}/basic-metrics")
    public Result<List<ThreadPoolBaseMetricsRespDTO>> listBasicMetrics(
            @PathVariable String namespace,
            @PathVariable String serviceName,
            @PathVariable String threadPoolId) {
        return Result.success(threadPoolInstanceService.listBasicMetrics(namespace, serviceName, threadPoolId));
    }

    /**
     * 获取动态线程池的完整运行时状态
     */
    @GetMapping("/api/anticipa-dashboard/thread-pool/{threadPoolId}/{networkAddress}")
    public Result<ThreadPoolStateRespDTO> getRuntimeState(
            @PathVariable String threadPoolId,
            @PathVariable String networkAddress) {
        return Result.success(threadPoolInstanceService.getRuntimeState(threadPoolId, networkAddress));
    }
}

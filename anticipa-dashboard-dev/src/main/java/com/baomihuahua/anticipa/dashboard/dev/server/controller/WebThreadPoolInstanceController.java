package com.baomihuahua.anticipa.dashboard.dev.server.controller;

import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolBaseMetricsRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolStateRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.service.WebThreadPoolInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class WebThreadPoolInstanceController {

    private final WebThreadPoolInstanceService webThreadPoolInstanceService;

    /**
     * 获取线程池列表
     */
    @GetMapping("/api/anticipa-dashboard/web/thread-pools/{namespace}/{serviceName}/basic-metrics")
    public Result<List<WebThreadPoolBaseMetricsRespDTO>> listBasicMetrics(
            @PathVariable String namespace,
            @PathVariable String serviceName) {
        return Result.success(webThreadPoolInstanceService.listBasicMetrics(namespace, serviceName));
    }

    /**
     * 获取线程池的完整运行时状态
     */
    @GetMapping("/api/anticipa-dashboard/web/thread-pool/{networkAddress}")
    public Result<WebThreadPoolStateRespDTO> getRuntimeState(@PathVariable String networkAddress) {
        return Result.success(webThreadPoolInstanceService.getRuntimeState(networkAddress));
    }
}

package com.baomihuahua.anticipa.dashboard.dev.server.controller;

import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolBaseMetricsRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolStateRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.service.ThreadPoolInstanceService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ThreadPoolInstanceController {

    private final ThreadPoolInstanceService threadPoolInstanceService;

    @GetMapping("/api/anticipa-dashboard/thread-pool/instance/basic-metrics")
    public Result<List<ThreadPoolBaseMetricsRespDTO>> listBasicMetrics(
            @NotBlank(message = "命名空间不为空") String namespace,
            @NotBlank(message = "服务名称不为空") String serviceName,
            @NotBlank(message = "线程池ID不为空") String threadPoolId) {
        return Result.success(threadPoolInstanceService.listBasicMetrics(namespace, serviceName, threadPoolId));
    }

    @GetMapping("/api/anticipa-dashboard/thread-pool/instance/runtime-state")
    public Result<ThreadPoolStateRespDTO> getRuntimeState(
            @NotBlank(message = "线程池ID不为空") String threadPoolId,
            @NotBlank(message = "网络地址不为空") String networkAddress) {
        return Result.success(threadPoolInstanceService.getRuntimeState(threadPoolId, networkAddress));
    }
}

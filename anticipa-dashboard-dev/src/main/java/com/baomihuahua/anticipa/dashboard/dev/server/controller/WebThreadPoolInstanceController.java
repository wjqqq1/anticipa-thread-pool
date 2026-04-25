package com.baomihuahua.anticipa.dashboard.dev.server.controller;

import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolBaseMetricsRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolStateRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.service.WebThreadPoolInstanceService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class WebThreadPoolInstanceController {

    private final WebThreadPoolInstanceService webThreadPoolInstanceService;

    @GetMapping("/api/anticipa-dashboard/web/thread-pool/instance/basic-metrics")
    public Result<List<WebThreadPoolBaseMetricsRespDTO>> listBasicMetrics(
            @NotBlank(message = "命名空间不为空") String namespace,
            @NotBlank(message = "服务名称不为空") String serviceName) {
        return Result.success(webThreadPoolInstanceService.listBasicMetrics(namespace, serviceName));
    }

    @GetMapping("/api/anticipa-dashboard/web/thread-pool/instance/runtime-state")
    public Result<WebThreadPoolStateRespDTO> getRuntimeState(
            @NotBlank(message = "网络地址不为空") String networkAddress) {
        return Result.success(webThreadPoolInstanceService.getRuntimeState(networkAddress));
    }
}

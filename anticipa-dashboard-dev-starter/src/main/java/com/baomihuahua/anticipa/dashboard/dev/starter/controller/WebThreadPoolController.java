package com.baomihuahua.anticipa.dashboard.dev.starter.controller;

import com.baomihuahua.anticipa.dashboard.dev.starter.core.Result;
import com.baomihuahua.anticipa.dashboard.dev.starter.core.Results;
import com.baomihuahua.anticipa.dashboard.dev.starter.dto.WebThreadPoolDashBoardDevRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.starter.service.WebThreadPoolService;
import com.baomihuahua.anticipa.web.starter.core.WebThreadPoolBaseMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WebThreadPoolController {

    private final WebThreadPoolService webThreadPoolService;

    /**
     * 获取 Web 线程池的轻量级运行指标
     */
    @GetMapping("/web/thread-pool/basic-metrics")
    public Result<WebThreadPoolBaseMetrics> getBasicMetrics() {
        return Results.success(webThreadPoolService.getBasicMetrics());
    }

    /**
     * 获取 Web 线程池的完整运行时状态
     */
    @GetMapping("/web/thread-pool")
    public Result<WebThreadPoolDashBoardDevRespDTO> getRuntimeInfo() {
        return Results.success(webThreadPoolService.getRuntimeInfo());
    }
}

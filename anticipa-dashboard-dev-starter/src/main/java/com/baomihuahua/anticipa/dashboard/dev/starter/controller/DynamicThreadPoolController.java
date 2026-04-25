package com.baomihuahua.anticipa.dashboard.dev.starter.controller;

import com.baomihuahua.anticipa.dashboard.dev.starter.core.Result;
import com.baomihuahua.anticipa.dashboard.dev.starter.core.Results;
import com.baomihuahua.anticipa.dashboard.dev.starter.dto.*;
import com.baomihuahua.anticipa.dashboard.dev.starter.service.DynamicThreadPoolOperator;
import com.baomihuahua.anticipa.dashboard.dev.starter.service.DynamicThreadPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class DynamicThreadPoolController {

    private final DynamicThreadPoolService dynamicThreadPoolService;
    private final DynamicThreadPoolOperator dynamicThreadPoolOperator;

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Results.success(Map.of(
                "status", "UP",
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 获取实例信息
     */
    @GetMapping("/instance/info")
    public Result<InstanceInfoRespDTO> getInstanceInfo() {
        return Results.success(dynamicThreadPoolOperator.getInstanceInfo());
    }

    /**
     * 获取线程池的轻量级运行指标
     */
    @GetMapping("/dynamic/thread-pool/{threadPoolId}/basic-metrics")
    public Result<ThreadPoolDashBoardDevBaseMetricsRespDTO> getBasicMetrics(@PathVariable String threadPoolId) {
        return Results.success(dynamicThreadPoolService.getBasicMetrics(threadPoolId));
    }

    /**
     * 获取线程池的完整运行时状态
     */
    @GetMapping("/dynamic/thread-pool/{threadPoolId}")
    public Result<ThreadPoolDashBoardDevRespDTO> getRuntimeInfo(@PathVariable String threadPoolId) {
        return Results.success(dynamicThreadPoolService.getRuntimeInfo(threadPoolId));
    }

    /**
     * 获取全量快照
     */
    @GetMapping("/dynamic/thread-pool/snapshot")
    public Result<InstanceSnapshotDTO> getSnapshot() {
        return Results.success(dynamicThreadPoolOperator.getSnapshot());
    }

    /**
     * 调整线程池参数
     */
    @PostMapping("/dynamic/thread-pool/{threadPoolId}/adjust")
    public Result<AdjustResultDTO> adjust(@PathVariable String threadPoolId, @RequestBody PoolAdjustRequestDTO request) {
        request.setPoolId(threadPoolId);
        return Results.success(dynamicThreadPoolOperator.adjust(threadPoolId, request));
    }

    /**
     * 批量调整线程池参数
     */
    @PostMapping("/dynamic/thread-pool/batch-adjust")
    public Result<List<AdjustResultDTO>> batchAdjust(@RequestBody List<PoolAdjustRequestDTO> requests) {
        List<AdjustResultDTO> results = requests.stream()
                .map(req -> dynamicThreadPoolOperator.adjust(req.getPoolId(), req))
                .collect(Collectors.toList());
        return Results.success(results);
    }

    /**
     * 模拟调整效果
     */
    @PostMapping("/dynamic/thread-pool/{threadPoolId}/simulate")
    public Result<SimulateResultDTO> simulate(@PathVariable String threadPoolId, @RequestBody PoolAdjustRequestDTO request) {
        request.setPoolId(threadPoolId);
        return Results.success(dynamicThreadPoolOperator.simulate(threadPoolId, request));
    }

    /**
     * 获取线程池配置
     */
    @GetMapping("/dynamic/thread-pool/{threadPoolId}/config")
    public Result<PoolConfigRespDTO> getPoolConfig(@PathVariable String threadPoolId) {
        return Results.success(dynamicThreadPoolOperator.getPoolConfig(threadPoolId));
    }

    /**
     * 回滚到指定快照
     */
    @PostMapping("/dynamic/thread-pool/{threadPoolId}/rollback")
    public Result<AdjustResultDTO> rollback(@PathVariable String threadPoolId, @RequestBody Map<String, String> body) {
        String snapshotId = body.get("snapshotId");
        return Results.success(dynamicThreadPoolOperator.rollback(threadPoolId, snapshotId));
    }

    /**
     * 获取调整历史
     */
    @GetMapping("/dynamic/thread-pool/{threadPoolId}/adjust-history")
    public Result<List<AdjustRecordDTO>> getAdjustHistory(
            @PathVariable String threadPoolId,
            @RequestParam(defaultValue = "20") int limit) {
        return Results.success(dynamicThreadPoolOperator.getAdjustHistory(threadPoolId, limit));
    }
}

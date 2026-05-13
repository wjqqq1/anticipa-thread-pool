package com.baomihuahua.anticipa.dashboard.dev.starter.controller;

import com.baomihuahua.anticipa.core.monitor.ThreadPoolLogRecord;
import com.baomihuahua.anticipa.core.monitor.ThreadPoolLogStore;
import com.baomihuahua.anticipa.dashboard.dev.starter.core.Result;
import com.baomihuahua.anticipa.dashboard.dev.starter.core.Results;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 线程池运行日志查询 REST 接口。
 */
@RestController
@RequestMapping("/api/anticipa-dashboard/logs")
public class ThreadPoolLogController {

    private final ThreadPoolLogStore logStore;

    public ThreadPoolLogController(ThreadPoolLogStore logStore) {
        this.logStore = logStore;
    }

    /**
     * 查询线程池运行日志。
     *
     * @param threadPoolId 线程池 ID
     * @param startTime    开始时间（ISO 格式，如 2026-04-26T10:00:00）
     * @param endTime      结束时间（ISO 格式）
     * @param aggregation  聚合策略：RAW / MINUTE / HOUR
     * @param limit        最大返回条数，0 表示不限制（默认 0）
     * @return 日志记录列表
     */
    @GetMapping("/{threadPoolId}")
    public Result<List<ThreadPoolLogRecord>> query(
            @PathVariable String threadPoolId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "RAW") String aggregation,
            @RequestParam(defaultValue = "0") int limit) {

        Instant start = startTime != null ? parseFlexible(startTime) : Instant.now().minus(Duration.ofMinutes(30));
        Instant end = endTime != null ? parseFlexible(endTime) : Instant.now();

        ThreadPoolLogStore.Aggregation agg;
        try {
            agg = ThreadPoolLogStore.Aggregation.valueOf(aggregation.toUpperCase());
        } catch (Exception e) {
            agg = ThreadPoolLogStore.Aggregation.RAW;
        }

        List<ThreadPoolLogRecord> records = logStore.query(threadPoolId, start, end, agg, null, limit);
        return Results.success(records);
    }

    /**
     * 检查指定线程池是否存在历史日志数据。
     *
     * @param threadPoolId 线程池 ID
     * @return 是否存在日志数据
     */
    @GetMapping("/{threadPoolId}/exists")
    public Result<java.util.Map<String, Object>> exists(@PathVariable String threadPoolId) {
        boolean hasLogs = logStore.exists(threadPoolId);
        return Results.success(java.util.Map.of(
                "exists", hasLogs,
                "threadPoolId", threadPoolId
        ));
    }

    /**
     * 获取日志摘要统计。
     */
    @GetMapping("/{threadPoolId}/summary")
    public Result<ThreadPoolLogStore.LogSummary> summary(
            @PathVariable String threadPoolId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        Instant start = startTime != null ? parseFlexible(startTime) : Instant.now().minus(Duration.ofMinutes(30));
        Instant end = endTime != null ? parseFlexible(endTime) : Instant.now();

        ThreadPoolLogStore.LogSummary summary = logStore.summary(threadPoolId, start, end);
        return Results.success(summary);
    }

    /**
     * 兼容解析时间字符串，支持带时区的 ISO 格式和不带时区的本地时间格式。
     * <p>
     * 带时区：2026-05-08T23:13:00.008194Z 或 2026-05-08T23:13:00.008194+08:00
     * 不带时区：2026-05-08T23:13:00.008194（按系统默认时区处理）
     * </p>
     */
    private static Instant parseFlexible(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeException e) {
            // 不带时区偏移，按本地时间解析后转为 Instant
            LocalDateTime ldt = LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return ldt.atZone(ZoneId.systemDefault()).toInstant();
        }
    }
}

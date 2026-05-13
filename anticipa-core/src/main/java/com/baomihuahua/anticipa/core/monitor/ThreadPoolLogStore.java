package com.baomihuahua.anticipa.core.monitor;

import com.alibaba.fastjson2.JSON;
import com.baomihuahua.anticipa.core.config.ThreadPoolLogConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 线程池运行日志文件存储。
 * <p>
 * 采用 JSON Lines 格式存储，每行一条记录。
 * 目录结构：{storePath}/{threadPoolId}/threadpool_{threadPoolId}_{yyyyMMdd}.log
 * 使用双缓冲（内存队列 + 异步批量写入）避免阻塞采集线程。
 * </p>
 */
public class ThreadPoolLogStore {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolLogStore.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ThreadPoolLogConfig config;
    private final String basePath;
    private final ExecutorService writeExecutor;
    private final Map<String, BlockingQueue<String>> buffers = new ConcurrentHashMap<>();

    public ThreadPoolLogStore(ThreadPoolLogConfig config) {
        this.config = config;
        this.basePath = config.getStorePath();
        this.writeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "log-store-writer");
            t.setDaemon(true);
            return t;
        });
        ensureDir(basePath);
    }

    // ========== 写入 ==========

    /**
     * 追加一条日志记录（异步非阻塞）。
     */
    public void append(ThreadPoolLogRecord record) {
        if (!config.isEnabled()) {
            return;
        }
        String line = JSON.toJSONString(record);
        String dateKey = dateKey(record.getTimestamp());
        String bufferKey = record.getThreadPoolId() + ":" + dateKey;
        BlockingQueue<String> queue = buffers.computeIfAbsent(bufferKey, k -> new LinkedBlockingQueue<>(10000));
        if (!queue.offer(line)) {
            log.warn("[LogStore] buffer full for {}, dropping record", bufferKey);
            return;
        }
        // 触发异步写入
        writeExecutor.submit(() -> flushBuffer(record.getThreadPoolId(), dateKey));
    }

    /**
     * 强制刷新所有缓冲（应用关闭时调用）。
     */
    public void flushAll() {
        for (Map.Entry<String, BlockingQueue<String>> entry : buffers.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            if (parts.length == 2) {
                flushBuffer(parts[0], parts[1]);
            }
        }
    }

    private void flushBuffer(String threadPoolId, String dateKey) {
        String bufferKey = threadPoolId + ":" + dateKey;
        BlockingQueue<String> queue = buffers.get(bufferKey);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        Path filePath = getLogFilePath(threadPoolId, dateKey);
        ensureDir(filePath.getParent().toString());
        List<String> batch = new ArrayList<>();
        queue.drainTo(batch, 500);
        if (batch.isEmpty()) {
            return;
        }
        try {
            Files.write(filePath, batch, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("[LogStore] failed to write log file: {}", filePath, e);
            // 重新入队
            queue.addAll(batch);
        }
    }

    // ========== 查询 ==========

    /**
     * 聚合策略枚举。
     */
    public enum Aggregation {
        /** 原始数据 */
        RAW,
        /** 分钟聚合 */
        MINUTE,
        /** 小时聚合 */
        HOUR
    }

    /**
     * 查询指定线程池在时间范围内的运行日志。
     *
     * @param threadPoolId 线程池 ID
     * @param startTime    开始时间
     * @param endTime      结束时间
     * @param aggregation  聚合策略
     * @return 日志记录列表
     */
    public List<ThreadPoolLogRecord> query(String threadPoolId, Instant startTime, Instant endTime, Aggregation aggregation) {
        return query(threadPoolId, startTime, endTime, aggregation, null, 0);
    }

    /**
     * 查询指定线程池在时间范围内的运行日志，可按实例过滤。
     *
     * @param threadPoolId 线程池 ID
     * @param startTime    开始时间
     * @param endTime      结束时间
     * @param aggregation  聚合策略
     * @param instanceId   实例标识（ip:port），为空则返回所有实例的记录
     * @return 日志记录列表
     */
    public List<ThreadPoolLogRecord> query(String threadPoolId, Instant startTime, Instant endTime, Aggregation aggregation, String instanceId) {
        return query(threadPoolId, startTime, endTime, aggregation, instanceId, 0);
    }

    /**
     * 查询指定线程池在时间范围内的运行日志，可按实例过滤并限制返回条数。
     *
     * @param threadPoolId 线程池 ID
     * @param startTime    开始时间
     * @param endTime      结束时间
     * @param aggregation  聚合策略
     * @param instanceId   实例标识（ip:port），为空则返回所有实例的记录
     * @param limit        最大返回条数，0 或负数表示不限制
     * @return 日志记录列表
     */
    public List<ThreadPoolLogRecord> query(String threadPoolId, Instant startTime, Instant endTime,
                                            Aggregation aggregation, String instanceId, int limit) {
        if (limit > 0) {
            List<ThreadPoolLogRecord> picked = queryLatestInRangeReverse(threadPoolId, startTime, endTime, instanceId, limit);
            picked.sort(Comparator.comparingLong(ThreadPoolLogRecord::getTimestamp));
            if (aggregation == Aggregation.RAW) {
                return picked;
            }
            return aggregate(picked, aggregation);
        }

        List<String> dateKeys = getDateKeysInRange(startTime, endTime);
        List<ThreadPoolLogRecord> allRecords = new ArrayList<>();

        for (String dateKey : dateKeys) {
            Path filePath = getLogFilePath(threadPoolId, dateKey);
            if (!Files.exists(filePath)) {
                continue;
            }
            try {
                List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
                long startEpoch = startTime.toEpochMilli();
                long endEpoch = endTime.toEpochMilli();
                for (String line : lines) {
                    if (line.isBlank()) {
                        continue;
                    }
                    ThreadPoolLogRecord record = JSON.parseObject(line, ThreadPoolLogRecord.class);
                    long ts = record.getTimestamp();
                    if (ts >= startEpoch && ts <= endEpoch) {
                        if (instanceId == null || instanceId.isBlank() || instanceId.equals(record.getInstanceId())) {
                            allRecords.add(record);
                        }
                    }
                }
            } catch (IOException e) {
                log.error("[LogStore] failed to read log file: {}", filePath, e);
            }
        }

        allRecords.sort(Comparator.comparingLong(ThreadPoolLogRecord::getTimestamp));

        if (aggregation == Aggregation.RAW) {
            return allRecords;
        }
        return aggregate(allRecords, aggregation);
    }

    /**
     * 从各日日志文件末尾向前扫描，在时间窗内凑满至多 {@code limit} 条即停止，避免整文件读入。
     * <p>假设日志按采集时间顺序追加；多实例交错时仍以「文件中从后往前」最先遇到的匹配行为主。</p>
     */
    private List<ThreadPoolLogRecord> queryLatestInRangeReverse(String threadPoolId, Instant startTime, Instant endTime,
                                                                String instanceId, int limit) {
        List<String> dateKeys = new ArrayList<>(getDateKeysInRange(startTime, endTime));
        Collections.reverse(dateKeys);
        long startEpoch = startTime.toEpochMilli();
        long endEpoch = endTime.toEpochMilli();
        List<ThreadPoolLogRecord> picked = new ArrayList<>(limit);

        for (String dateKey : dateKeys) {
            if (picked.size() >= limit) {
                break;
            }
            Path filePath = getLogFilePath(threadPoolId, dateKey);
            if (!Files.exists(filePath)) {
                continue;
            }
            try {
                List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
                for (int i = lines.size() - 1; i >= 0 && picked.size() < limit; i--) {
                    String line = lines.get(i);
                    if (line.isBlank()) {
                        continue;
                    }
                    ThreadPoolLogRecord record = JSON.parseObject(line, ThreadPoolLogRecord.class);
                    long ts = record.getTimestamp();
                    // 日志按时间顺序追加，反向读取时遇到早于 startEpoch 的记录即可终止
                    if (ts < startEpoch) {
                        break;
                    }
                    if (ts <= endEpoch) {
                        if (instanceId == null || instanceId.isBlank() || instanceId.equals(record.getInstanceId())) {
                            picked.add(record);
                        }
                    }
                }
            } catch (IOException e) {
                log.error("[LogStore] failed to reverse-read log file: {}", filePath, e);
            }
        }
        return picked;
    }

    /**
     * 获取日志摘要统计（均值、峰值等）。
     */
    public LogSummary summary(String threadPoolId, Instant startTime, Instant endTime) {
        return summary(threadPoolId, startTime, endTime, null);
    }

    /**
     * 获取日志摘要统计，可按实例过滤。
     */
    public LogSummary summary(String threadPoolId, Instant startTime, Instant endTime, String instanceId) {
        // 传入 limit 以使用反向读取，避免全量加载日志文件
        List<ThreadPoolLogRecord> records = query(threadPoolId, startTime, endTime, Aggregation.RAW, instanceId, 10000);
        if (records.isEmpty()) {
            return new LogSummary();
        }
        LogSummary summary = new LogSummary();
        summary.setRecordCount(records.size());
        summary.setStartTime(startTime);
        summary.setEndTime(endTime);

        java.util.IntSummaryStatistics activeStats = records.stream().mapToInt(ThreadPoolLogRecord::getActiveCount).summaryStatistics();
        summary.setAvgActiveCount((int) Math.round(activeStats.getAverage()));
        summary.setMaxActiveCount(activeStats.getMax());

        java.util.IntSummaryStatistics poolStats = records.stream().mapToInt(ThreadPoolLogRecord::getPoolSize).summaryStatistics();
        summary.setAvgPoolSize((int) Math.round(poolStats.getAverage()));
        summary.setMaxPoolSize(poolStats.getMax());

        // 队列使用率
        double avgQueueUsage = records.stream()
                .filter(r -> r.getQueueCapacity() > 0)
                .mapToDouble(r -> r.getQueueSize() * 100.0 / r.getQueueCapacity())
                .average().orElse(0);
        summary.setAvgQueueUsagePercent((int) Math.round(avgQueueUsage));

        double maxQueueUsage = records.stream()
                .filter(r -> r.getQueueCapacity() > 0)
                .mapToDouble(r -> r.getQueueSize() * 100.0 / r.getQueueCapacity())
                .max().orElse(0);
        summary.setMaxQueueUsagePercent((int) Math.round(maxQueueUsage));

        summary.setTotalRejectCount(records.stream().mapToInt(ThreadPoolLogRecord::getRejectCount).sum());
        summary.setThreadPoolId(threadPoolId);
        return summary;
    }

    // ========== 清理 ==========

    /**
     * 清理超过保留天数的旧日志文件。
     */
    public void cleanExpired() {
        File baseDir = new File(basePath);
        if (!baseDir.exists()) {
            return;
        }
        LocalDate cutoff = LocalDate.now().minusDays(config.getRetentionDays());
        String cutoffStr = cutoff.format(DATE_FORMAT);
        File[] poolDirs = baseDir.listFiles(File::isDirectory);
        if (poolDirs == null) {
            return;
        }
        for (File poolDir : poolDirs) {
            File[] logFiles = poolDir.listFiles((dir, name) -> name.endsWith(".log") || name.endsWith(".log.gz"));
            if (logFiles == null) {
                continue;
            }
            for (File f : logFiles) {
                // 文件名格式：threadpool_{poolId}_{yyyyMMdd}.log 或 threadpool_{poolId}_{yyyyMMdd}.log.gz
                String name = f.getName();
                String dateStr = name.replace(".log", "").replace(".gz", "");
                // 提取末尾的日期部分（yyyyMMdd）
                int lastUnderscore = dateStr.lastIndexOf('_');
                if (lastUnderscore >= 0) {
                    dateStr = dateStr.substring(lastUnderscore + 1);
                }
                if (dateStr.compareTo(cutoffStr) < 0) {
                    if (f.delete()) {
                        log.info("[LogStore] cleaned expired log: {}", f.getAbsolutePath());
                    }
                }
            }
        }
    }

    // ========== 内部方法 ==========

    private List<ThreadPoolLogRecord> aggregate(List<ThreadPoolLogRecord> records, Aggregation aggregation) {
        if (records.isEmpty()) {
            return records;
        }
        long windowMs;
        if (aggregation == Aggregation.MINUTE) {
            windowMs = 60_000L;
        } else {
            windowMs = 3_600_000L;
        }

        Map<Long, List<ThreadPoolLogRecord>> grouped = new LinkedHashMap<>();
        for (ThreadPoolLogRecord r : records) {
            long bucket = (r.getTimestamp() / windowMs) * windowMs;
            grouped.computeIfAbsent(bucket, k -> new ArrayList<>()).add(r);
        }

        List<ThreadPoolLogRecord> result = new ArrayList<>();
        for (Map.Entry<Long, List<ThreadPoolLogRecord>> entry : grouped.entrySet()) {
            List<ThreadPoolLogRecord> group = entry.getValue();
            ThreadPoolLogRecord avg = new ThreadPoolLogRecord();
            avg.setThreadPoolId(records.get(0).getThreadPoolId());
            avg.setTimestamp(entry.getKey());
            avg.setCorePoolSize(group.get(0).getCorePoolSize());
            avg.setMaximumPoolSize(group.get(0).getMaximumPoolSize());
            avg.setQueueCapacity(group.get(0).getQueueCapacity());
            avg.setPoolSize((int) Math.round(group.stream().mapToInt(ThreadPoolLogRecord::getPoolSize).average().orElse(0)));
            avg.setActiveCount((int) Math.round(group.stream().mapToInt(ThreadPoolLogRecord::getActiveCount).average().orElse(0)));
            avg.setLargestPoolSize(group.stream().mapToInt(ThreadPoolLogRecord::getLargestPoolSize).max().orElse(0));
            avg.setCompletedTaskCount(group.get(group.size() - 1).getCompletedTaskCount());
            avg.setQueueSize((int) Math.round(group.stream().mapToInt(ThreadPoolLogRecord::getQueueSize).average().orElse(0)));
            avg.setQueueRemainingCapacity((int) Math.round(group.stream().mapToInt(ThreadPoolLogRecord::getQueueRemainingCapacity).average().orElse(0)));
            avg.setRejectCount(group.stream().mapToInt(ThreadPoolLogRecord::getRejectCount).sum());
            avg.setRejectedHandler(group.get(0).getRejectedHandler());
            result.add(avg);
        }
        return result;
    }

    private Path getLogFilePath(String threadPoolId, String dateKey) {
        return Paths.get(basePath, threadPoolId, "threadpool_" + threadPoolId + "_" + dateKey + ".log");
    }

    /**
     * 检查指定线程池是否存在历史日志数据。
     *
     * @param threadPoolId 线程池 ID
     * @return 如果该线程池的日志目录存在且包含 .log 文件，返回 true
     */
    public boolean exists(String threadPoolId) {
        File poolDir = new File(basePath, threadPoolId);
        if (!poolDir.exists() || !poolDir.isDirectory()) {
            return false;
        }
        File[] logFiles = poolDir.listFiles((dir, name) -> name.endsWith(".log") || name.endsWith(".log.gz"));
        return logFiles != null && logFiles.length > 0;
    }

    private String dateKey(long epochMillis) {
        LocalDate date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate();
        return date.format(DATE_FORMAT);
    }

    private List<String> getDateKeysInRange(Instant start, Instant end) {
        List<String> keys = new ArrayList<>();
        LocalDate startDate = start.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = end.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            keys.add(current.format(DATE_FORMAT));
            current = current.plusDays(1);
        }
        return keys;
    }

    private void ensureDir(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    // ========== 摘要统计 ==========

    public static class LogSummary {
        private String threadPoolId;
        private Instant startTime;
        private Instant endTime;
        private int recordCount;
        private int avgActiveCount;
        private int maxActiveCount;
        private int avgPoolSize;
        private int maxPoolSize;
        private int avgQueueUsagePercent;
        private int maxQueueUsagePercent;
        private long totalRejectCount;

        public String getThreadPoolId() { return threadPoolId; }
        public void setThreadPoolId(String threadPoolId) { this.threadPoolId = threadPoolId; }
        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
        public int getRecordCount() { return recordCount; }
        public void setRecordCount(int recordCount) { this.recordCount = recordCount; }
        public int getAvgActiveCount() { return avgActiveCount; }
        public void setAvgActiveCount(int avgActiveCount) { this.avgActiveCount = avgActiveCount; }
        public int getMaxActiveCount() { return maxActiveCount; }
        public void setMaxActiveCount(int maxActiveCount) { this.maxActiveCount = maxActiveCount; }
        public int getAvgPoolSize() { return avgPoolSize; }
        public void setAvgPoolSize(int avgPoolSize) { this.avgPoolSize = avgPoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getAvgQueueUsagePercent() { return avgQueueUsagePercent; }
        public void setAvgQueueUsagePercent(int avgQueueUsagePercent) { this.avgQueueUsagePercent = avgQueueUsagePercent; }
        public int getMaxQueueUsagePercent() { return maxQueueUsagePercent; }
        public void setMaxQueueUsagePercent(int maxQueueUsagePercent) { this.maxQueueUsagePercent = maxQueueUsagePercent; }
        public long getTotalRejectCount() { return totalRejectCount; }
        public void setTotalRejectCount(long totalRejectCount) { this.totalRejectCount = totalRejectCount; }
    }
}

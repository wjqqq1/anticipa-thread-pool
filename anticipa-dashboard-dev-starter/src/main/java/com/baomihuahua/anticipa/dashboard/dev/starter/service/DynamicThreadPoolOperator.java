package com.baomihuahua.anticipa.dashboard.dev.starter.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.baomihuahua.anticipa.core.executor.AnticipaExecutor;
import com.baomihuahua.anticipa.core.executor.AnticipaRegistry;
import com.baomihuahua.anticipa.core.executor.ThreadPoolExecutorHolder;
import com.baomihuahua.anticipa.core.executor.support.ResizableCapacityLinkedBlockingQueue;
import com.baomihuahua.anticipa.dashboard.dev.starter.dto.*;
import com.baomihuahua.anticipa.dashboard.dev.starter.store.AdjustHistoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class DynamicThreadPoolOperator {

    @Value("${server.port:8080}")
    private String port;

    @Value("${spring.application.name:unknown}")
    private String appName;

    @Value("${spring.profiles.active:UNKNOWN}")
    private String activeProfile;

    private final AdjustHistoryStore historyStore = new AdjustHistoryStore();

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

    public InstanceSnapshotDTO getSnapshot() {
        Collection<ThreadPoolExecutorHolder> holders = AnticipaRegistry.getAllHolders();
        List<ThreadPoolSummaryDTO> pools = holders.stream()
                .map(this::captureSummary)
                .collect(Collectors.toList());
        return InstanceSnapshotDTO.builder()
                .instanceInfo(getInstanceInfo())
                .timestamp(System.currentTimeMillis())
                .threadPools(pools)
                .build();
    }

    public AdjustSnapshotDTO captureSnapshot(String threadPoolId) {
        ThreadPoolExecutorHolder holder = getHolder(threadPoolId);
        ThreadPoolExecutor executor = holder.getExecutor();
        BlockingQueue<?> queue = executor.getQueue();
        long rejectCount = -1L;
        if (executor instanceof AnticipaExecutor) {
            rejectCount = ((AnticipaExecutor) executor).getRejectCount().get();
        }
        int queueSize = queue.size();
        int queueCapacity = queueSize + queue.remainingCapacity();
        return AdjustSnapshotDTO.builder()
                .corePoolSize(executor.getCorePoolSize())
                .maximumPoolSize(executor.getMaximumPoolSize())
                .activeCount(executor.getActiveCount())
                .queueSize(queueSize)
                .queueCapacity(queueCapacity)
                .completedTaskCount(executor.getCompletedTaskCount())
                .rejectCount(rejectCount)
                .coreUsageRate(calcRate(executor.getActiveCount(), executor.getCorePoolSize()))
                .queueUsageRate(calcRate(queueSize, queueCapacity))
                .build();
    }

    public AdjustResultDTO adjust(String threadPoolId, PoolAdjustRequestDTO request) {
        AdjustSnapshotDTO before = captureSnapshot(threadPoolId);
        ThreadPoolExecutorHolder holder = getHolder(threadPoolId);
        ThreadPoolExecutor executor = holder.getExecutor();
        List<String> changedFields = new ArrayList<>();

        if (request.getCorePoolSize() != null) {
            executor.setCorePoolSize(request.getCorePoolSize());
            changedFields.add("corePoolSize");
        }
        if (request.getMaximumPoolSize() != null) {
            executor.setMaximumPoolSize(request.getMaximumPoolSize());
            changedFields.add("maximumPoolSize");
        }
        if (request.getKeepAliveTime() != null) {
            executor.setKeepAliveTime(request.getKeepAliveTime(), TimeUnit.SECONDS);
            changedFields.add("keepAliveTime");
        }
        if (request.getRejectedHandler() != null) {
            executor.setRejectedExecutionHandler(parseRejectedHandler(request.getRejectedHandler()));
            changedFields.add("rejectedHandler");
        }
        if (request.getAllowCoreThreadTimeOut() != null) {
            executor.allowCoreThreadTimeOut(request.getAllowCoreThreadTimeOut());
            changedFields.add("allowCoreThreadTimeOut");
        }
        if (request.getQueueCapacity() != null && executor.getQueue() instanceof ResizableCapacityLinkedBlockingQueue) {
            ((ResizableCapacityLinkedBlockingQueue<?>) executor.getQueue()).setCapacity(request.getQueueCapacity());
            changedFields.add("queueCapacity");
        }

        AdjustSnapshotDTO after = captureSnapshot(threadPoolId);

        AdjustRecordDTO record = AdjustRecordDTO.builder()
                .snapshotId(IdUtil.fastSimpleUUID())
                .timestamp(System.currentTimeMillis())
                .source(request.getSource())
                .reason(request.getReason())
                .before(before)
                .after(after)
                .changedFields(changedFields)
                .build();
        historyStore.record(threadPoolId, record);

        log.info("[ADJUST] {} - fields={}, before={}, after={}", threadPoolId, changedFields, before, after);

        return AdjustResultDTO.builder()
                .poolId(threadPoolId)
                .success(true)
                .before(before)
                .after(after)
                .adjustedFields(changedFields)
                .message("运行时调整成功")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public SimulateResultDTO simulate(String threadPoolId, PoolAdjustRequestDTO request) {
        AdjustSnapshotDTO current = captureSnapshot(threadPoolId);
        int simulatedCore = request.getCorePoolSize() != null ? request.getCorePoolSize() : current.getCorePoolSize();
        int simulatedMax = request.getMaximumPoolSize() != null ? request.getMaximumPoolSize() : current.getMaximumPoolSize();
        int simulatedQueue = request.getQueueCapacity() != null ? request.getQueueCapacity() : current.getQueueCapacity();

        AdjustSnapshotDTO simulated = AdjustSnapshotDTO.builder()
                .corePoolSize(simulatedCore)
                .maximumPoolSize(simulatedMax)
                .activeCount(current.getActiveCount())
                .queueSize(current.getQueueSize())
                .queueCapacity(simulatedQueue)
                .completedTaskCount(current.getCompletedTaskCount())
                .rejectCount(current.getRejectCount())
                .coreUsageRate(calcRate(current.getActiveCount(), simulatedCore))
                .queueUsageRate(calcRate(current.getQueueSize(), simulatedQueue))
                .build();

        List<String> effects = new ArrayList<>();
        if (simulated.getCoreUsageRate() < current.getCoreUsageRate()) {
            effects.add("线程使用率从 " + current.getCoreUsageRate() + "% 降至 " + simulated.getCoreUsageRate() + "%");
        }
        if (simulated.getQueueUsageRate() < current.getQueueUsageRate()) {
            effects.add("队列使用率从 " + current.getQueueUsageRate() + "% 降至 " + simulated.getQueueUsageRate() + "%");
        }
        if (effects.isEmpty()) {
            effects.add("参数调整后指标不会有明显变化，建议检查其他瓶颈");
        }

        String riskLevel = evaluateRisk(current, simulated);
        List<String> warnings = generateRiskWarnings(current, simulated);

        return SimulateResultDTO.builder()
                .poolId(threadPoolId)
                .currentConfig(current)
                .simulatedConfig(simulated)
                .expectedEffects(effects)
                .riskLevel(riskLevel)
                .riskWarnings(warnings)
                .build();
    }

    public AdjustResultDTO rollback(String threadPoolId, String snapshotId) {
        AdjustRecordDTO record = historyStore.getSnapshot(threadPoolId, snapshotId);
        if (record == null) {
            throw new RuntimeException("快照不存在: " + snapshotId);
        }
        AdjustSnapshotDTO target = record.getBefore();
        PoolAdjustRequestDTO rollbackReq = PoolAdjustRequestDTO.builder()
                .corePoolSize(target.getCorePoolSize())
                .maximumPoolSize(target.getMaximumPoolSize())
                .queueCapacity(target.getQueueCapacity())
                .source("ROLLBACK")
                .reason("回滚到快照: " + snapshotId)
                .build();
        return adjust(threadPoolId, rollbackReq);
    }

    public PoolConfigRespDTO getPoolConfig(String threadPoolId) {
        ThreadPoolExecutorHolder holder = getHolder(threadPoolId);
        ThreadPoolExecutor executor = holder.getExecutor();
        BlockingQueue<?> queue = executor.getQueue();
        return PoolConfigRespDTO.builder()
                .threadPoolId(threadPoolId)
                .corePoolSize(executor.getCorePoolSize())
                .maximumPoolSize(executor.getMaximumPoolSize())
                .keepAliveTime(executor.getKeepAliveTime(TimeUnit.SECONDS))
                .queueType(queue.getClass().getSimpleName())
                .queueCapacity(queue.size() + queue.remainingCapacity())
                .rejectedHandler(executor.getRejectedExecutionHandler().toString())
                .allowCoreThreadTimeOut(executor.allowsCoreThreadTimeOut())
                .build();
    }

    public List<AdjustRecordDTO> getAdjustHistory(String threadPoolId, int limit) {
        return historyStore.getHistory(threadPoolId, limit);
    }

    private ThreadPoolExecutorHolder getHolder(String threadPoolId) {
        ThreadPoolExecutorHolder holder = AnticipaRegistry.getHolder(threadPoolId);
        if (holder == null) {
            throw new RuntimeException("线程池不存在: " + threadPoolId);
        }
        return holder;
    }

    private ThreadPoolSummaryDTO captureSummary(ThreadPoolExecutorHolder holder) {
        ThreadPoolExecutor executor = holder.getExecutor();
        BlockingQueue<?> queue = executor.getQueue();
        int queueSize = queue.size();
        return ThreadPoolSummaryDTO.builder()
                .poolId(holder.getThreadPoolId())
                .corePoolSize(executor.getCorePoolSize())
                .maximumPoolSize(executor.getMaximumPoolSize())
                .activeCount(executor.getActiveCount())
                .queueSize(queueSize)
                .queueCapacity(queueSize + queue.remainingCapacity())
                .completedTaskCount(executor.getCompletedTaskCount())
                .rejectCount(executor instanceof AnticipaExecutor ? ((AnticipaExecutor) executor).getRejectCount().get() : -1L)
                .build();
    }

    private String getLocalIp() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private double calcRate(long numerator, long denominator) {
        return denominator > 0 ? Math.round(numerator * 1000.0 / denominator) / 10.0 : 0.0;
    }

    private RejectedExecutionHandler parseRejectedHandler(String name) {
        switch (name) {
            case "CallerRunsPolicy": return new ThreadPoolExecutor.CallerRunsPolicy();
            case "AbortPolicy": return new ThreadPoolExecutor.AbortPolicy();
            case "DiscardPolicy": return new ThreadPoolExecutor.DiscardPolicy();
            case "DiscardOldestPolicy": return new ThreadPoolExecutor.DiscardOldestPolicy();
            default: throw new RuntimeException("不支持的拒绝策略: " + name);
        }
    }

    private String evaluateRisk(AdjustSnapshotDTO cur, AdjustSnapshotDTO sim) {
        boolean hasMajorChange = Math.abs(sim.getCorePoolSize() - cur.getCorePoolSize()) > cur.getCorePoolSize() * 0.5
                || Math.abs(sim.getQueueCapacity() - cur.getQueueCapacity()) > cur.getQueueCapacity() * 0.5;
        boolean hasReduction = sim.getCorePoolSize() < cur.getCorePoolSize() || sim.getQueueCapacity() < cur.getQueueCapacity();
        if (hasMajorChange && hasReduction) return "HIGH";
        if (hasMajorChange || hasReduction) return "MEDIUM";
        return "LOW";
    }

    private List<String> generateRiskWarnings(AdjustSnapshotDTO cur, AdjustSnapshotDTO sim) {
        List<String> warnings = new ArrayList<>();
        if (sim.getCorePoolSize() < cur.getCorePoolSize()) {
            warnings.add("减少核心线程数可能导致线程全部占满");
        }
        if (sim.getQueueCapacity() < cur.getQueueCapacity() && cur.getQueueUsageRate() > 50) {
            warnings.add("当前队列使用率 " + cur.getQueueUsageRate() + "% ，减小容量可能导致任务被拒绝");
        }
        return warnings;
    }
}

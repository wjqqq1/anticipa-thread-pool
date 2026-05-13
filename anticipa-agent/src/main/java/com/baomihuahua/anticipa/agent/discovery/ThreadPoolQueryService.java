package com.baomihuahua.anticipa.agent.discovery;

import java.util.List;
import java.util.Map;

/**
 * 线程池查询服务抽象接口。
 * <p>
 * 为 Agent 工具提供统一的线程池查询能力。
 * 查询指标必须带 namespace + serviceName，
 * 由 Nacos 精确定位实例列表，而非通过 threadPoolId 暴力遍历所有实例。
 * <p>
 * 通过 Nacos 精确查询 + HTTP 拉取客户端指标。
 * </p>
 */
public interface ThreadPoolQueryService {

    /**
     * 查询指定线程池的运行时指标。
     *
     * @param namespace    Nacos 命名空间（必填）
     * @param serviceName  Nacos 服务名（必填）
     * @param threadPoolId 线程池 ID
     * @param instanceId   实例 ID（可选，为空则返回该服务下所有实例的指标）
     * @return 线程池指标 Map，key 为指标名，value 为指标值；找不到时返回 null
     */
    Map<String, Object> queryPoolMetrics(String namespace, String serviceName, String threadPoolId, String instanceId);

    /**
     * 调整指定线程池的运行时参数。
     *
     * @param namespace    Nacos 命名空间（必填）
     * @param serviceName  Nacos 服务名（必填）
     * @param threadPoolId 线程池 ID
     * @param instanceId   实例 ID（可选）
     * @param params       待调整参数，key 可为 corePoolSize / maximumPoolSize / keepAliveSeconds / queueCapacity
     * @return 调整结果 Map，包含 before / after / changes 等信息；失败时返回 null
     */
    Map<String, Object> adjustPool(String namespace, String serviceName, String threadPoolId, String instanceId, Map<String, Object> params);

    /**
     * 列出当前可用的所有线程池 ID。
     *
     * @return 线程池 ID 列表
     */
    List<String> listPoolIds();

    /**
     * 列出指定服务下的所有线程池 ID。
     * <p>
     * 通过 Nacos 按服务名精准查询，避免全量扫描。
     * </p>
     *
     * @param serviceName 服务名称
     * @return 该服务下的线程池 ID 列表
     */
    default List<String> listPoolIdsByService(String serviceName) {
        return listPoolIds();
    }

    /**
     * 列出指定命名空间和服务下的所有线程池 ID。
     *
     * @param namespace   Nacos 命名空间
     * @param serviceName 服务名称
     * @return 该命名空间下该服务的线程池 ID 列表
     */
    default List<String> listPoolIdsByService(String namespace, String serviceName) {
        return listPoolIdsByService(serviceName);
    }

    /**
     * 列出指定实例上的所有线程池详情（含运行时指标摘要）。
     * <p>
     * 通过 HTTP 调用目标实例的 /snapshot 端点获取，返回每个线程池的 ID 和基本指标。
     * </p>
     *
     * @param namespace   Nacos 命名空间
     * @param serviceName 服务名称
     * @param instanceId  实例 ID，格式 "ip:port"
     * @return 该实例上的线程池详情列表，每个 Map 包含 poolId 及基本指标；找不到时返回空列表
     */
    default List<Map<String, Object>> listInstancePoolDetails(String namespace, String serviceName, String instanceId) {
        return java.util.Collections.emptyList();
    }

    /**
     * 列出指定命名空间下的 Nacos 配置文件 dataId 列表。
     * <p>
     * 通过 Nacos Open API 查询。
     * </p>
     *
     * @param namespace Nacos 命名空间
     * @return 配置文件 dataId 列表
     */
    default List<String> listNacosConfigs(String namespace) {
        return java.util.Collections.emptyList();
    }

    /**
     * 修改 Nacos 配置文件中的线程池参数（持久化，影响所有使用该配置的实例）。
     * <p>
     * 读取 Nacos 中指定的配置文件 YAML，找到目标线程池定义，修改参数后发布回 Nacos。
     * 所有使用该配置的客户端实例将通过 Nacos 监听器自动刷新。
     *
     * @param namespace    Nacos 命名空间（必填）
     * @param dataId       Nacos 配置文件 dataId（必填，如 "order-service.yaml"）
     * @param group        Nacos 配置分组（可选，默认 DEFAULT_GROUP）
     * @param threadPoolId 线程池 ID（必填）
     * @param params       待修改参数，key 可为 corePoolSize / maximumPoolSize / keepAliveTime / queueCapacity
     * @return 修改结果 Map，包含 before / after / changes / dataId / group / affectedInstanceCount；
     *         失败时返回含 "error" key 的 Map
     */
    Map<String, Object> updateConfig(String namespace, String dataId, String group,
                                      String threadPoolId, Map<String, Object> params);

    // ─── 日志查询 ───

    /**
     * 检查指定线程池是否存在历史运行日志数据。
     * <p>
     * 通过 HTTP 调用客户端实例的日志端点获取。
     * </p>
     *
     * @param namespace   Nacos 命名空间（必填）
     * @param serviceName 服务名称（必填）
     * @param threadPoolId 线程池 ID
     * @param instanceId   实例 ID（可选，为空则检查服务下所有实例）
     * @return 结果 Map，包含 exists（boolean）和 threadPoolId
     */
    default Map<String, Object> checkLogsExist(String namespace, String serviceName,
                                                 String threadPoolId, String instanceId) {
        return Map.of("exists", false, "threadPoolId", threadPoolId);
    }

    /**
     * 查询指定线程池的历史运行指标。
     * <p>
     * 通过 HTTP 调用客户端实例的日志端点获取。
     * </p>
     *
     * @param namespace   Nacos 命名空间（必填）
     * @param serviceName 服务名称（必填）
     * @param threadPoolId 线程池 ID
     * @param instanceId   实例 ID（可选，为空则查询服务下所有实例）
     * @param startTime    开始时间（ISO 格式，可为空）
     * @param endTime      结束时间（ISO 格式，可为空）
     * @param aggregation  聚合策略：RAW / MINUTE / HOUR（可为空，默认 HOUR）
     * @param limit        最大返回条数，0 或负数表示不限制
     * @return 结果 Map，包含 threadPoolId、recordCount、records 等
     */
    default Map<String, Object> queryHistory(String namespace, String serviceName, String threadPoolId,
                                              String instanceId, String startTime, String endTime,
                                              String aggregation, int limit) {
        return Map.of("threadPoolId", threadPoolId, "recordCount", 0, "records", java.util.Collections.emptyList());
    }

    /**
     * 分析指定线程池的运行趋势摘要。
     * <p>
     * 通过 HTTP 调用客户端实例的日志端点获取。
     * </p>
     *
     * @param namespace   Nacos 命名空间（必填）
     * @param serviceName 服务名称（必填）
     * @param threadPoolId 线程池 ID
     * @param instanceId   实例 ID（可选，为空则分析服务下所有实例）
     * @param startTime    开始时间（ISO 格式，可为空）
     * @param endTime      结束时间（ISO 格式，可为空）
     * @return 结果 Map，包含 trend summary 指标
     */
    default Map<String, Object> analyzeTrends(String namespace, String serviceName, String threadPoolId,
                                               String instanceId, String startTime, String endTime) {
        return Map.of("threadPoolId", threadPoolId, "recordCount", 0);
    }

    // ─── 直调模式（跳过 Nacos 发现，直接用 instanceId 调用目标实例） ───

    /**
     * 直接通过 instanceId 查询指定线程池的运行时指标（跳过 Nacos 发现）。
     *
     * @param threadPoolId 线程池 ID
     * @param instanceId   实例 ID（必填，格式 ip:port）
     * @return 线程池指标 Map；找不到时返回 null
     */
    default Map<String, Object> queryPoolMetricsDirect(String threadPoolId, String instanceId) {
        return queryPoolMetrics(null, null, threadPoolId, instanceId);
    }

    /**
     * 直接通过 instanceId 查询指定线程池的历史运行指标（跳过 Nacos 发现）。
     *
     * @param threadPoolId 线程池 ID
     * @param instanceId   实例 ID（必填，格式 ip:port）
     * @param startTime    开始时间（ISO 格式，可为空）
     * @param endTime      结束时间（ISO 格式，可为空）
     * @param aggregation  聚合策略：RAW / MINUTE / HOUR（可为空，默认 HOUR）
     * @param limit        最大返回条数，0 或负数表示不限制
     * @return 结果 Map，包含 threadPoolId、recordCount、records 等
     */
    default Map<String, Object> queryHistoryDirect(String threadPoolId, String instanceId,
                                                    String startTime, String endTime,
                                                    String aggregation, int limit) {
        return queryHistory(null, null, threadPoolId, instanceId, startTime, endTime, aggregation, limit);
    }

    /**
     * 直接通过 instanceId 检查指定线程池是否存在历史运行日志（跳过 Nacos 发现）。
     *
     * @param threadPoolId 线程池 ID
     * @param instanceId   实例 ID（必填，格式 ip:port）
     * @return 结果 Map，包含 exists（boolean）和 threadPoolId
     */
    default Map<String, Object> checkLogsExistDirect(String threadPoolId, String instanceId) {
        return checkLogsExist(null, null, threadPoolId, instanceId);
    }

    /**
     * 直接通过 instanceId 分析指定线程池的运行趋势摘要（跳过 Nacos 发现）。
     *
     * @param threadPoolId 线程池 ID
     * @param instanceId   实例 ID（必填，格式 ip:port）
     * @param startTime    开始时间（ISO 格式，可为空）
     * @param endTime      结束时间（ISO 格式，可为空）
     * @return 结果 Map，包含 trend summary 指标
     */
    default Map<String, Object> analyzeTrendsDirect(String threadPoolId, String instanceId,
                                                     String startTime, String endTime) {
        return analyzeTrends(null, null, threadPoolId, instanceId, startTime, endTime);
    }
}

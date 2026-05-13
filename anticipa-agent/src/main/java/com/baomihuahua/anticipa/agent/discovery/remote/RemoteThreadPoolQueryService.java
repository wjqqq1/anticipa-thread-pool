package com.baomihuahua.anticipa.agent.discovery.remote;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomihuahua.anticipa.agent.discovery.InstanceDiscoveryService;
import com.baomihuahua.anticipa.agent.discovery.InstanceInfo;
import com.baomihuahua.anticipa.agent.discovery.ThreadPoolQueryService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 远程线程池查询服务（控制台模式）。
 * <p>
 * 通过 Nacos 发现实例，通过 HTTP 调用客户端 Starter 暴露的端点获取线程池数据，
 * 通过 Nacos Open API 读取和修改配置文件。
 * </p>
 */
public class RemoteThreadPoolQueryService implements ThreadPoolQueryService {

    private static final Logger log = LoggerFactory.getLogger(RemoteThreadPoolQueryService.class);

    private final NacosHttpClient nacosHttpClient;
    private final InstanceDiscoveryService instanceDiscoveryService;

    private final ThreadPoolExecutor httpPoolExecutor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 10,
            Runtime.getRuntime().availableProcessors() * 10,
            60, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    public RemoteThreadPoolQueryService(NacosHttpClient nacosHttpClient,
                                         InstanceDiscoveryService instanceDiscoveryService) {
        this.nacosHttpClient = nacosHttpClient;
        this.instanceDiscoveryService = instanceDiscoveryService;
    }

    // ─── 查询 ───

    @Override
    public Map<String, Object> queryPoolMetrics(String namespace, String serviceName,
                                                 String threadPoolId, String instanceId) {
        if (namespace == null || namespace.isEmpty() || serviceName == null || serviceName.isEmpty()) {
            log.warn("[RemoteThreadPoolQueryService] namespace and serviceName are required");
            return null;
        }

        List<NacosHttpClient.NacosInstanceInfo> instances = nacosHttpClient.listInstances(namespace, serviceName);
        if (instances.isEmpty()) {
            return null;
        }

        // 如果指定了 instanceId，过滤（支持 "ip:port" 和 "serviceName:ip:port" 两种格式）
        if (instanceId != null && !instanceId.isEmpty()) {
            instances = instances.stream()
                    .filter(s -> {
                        String ipPort = s.getIp() + ":" + s.getPort();
                        return ipPort.equals(instanceId)
                                || (serviceName + ":" + ipPort).equals(instanceId);
                    })
                    .collect(Collectors.toList());
            if (instances.isEmpty()) {
                return null;
            }
        }

        // 并行 HTTP 调用
        List<Map<String, Object>> allMetrics = new ArrayList<>();
        List<CompletableFuture<Map<String, Object>>> futures = instances.stream()
                .map(inst -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String addr = inst.getIp() + ":" + inst.getPort();
                        String url = "http://" + addr
                                + "/api/anticipa-dashboard/dynamic/thread-pool/" + threadPoolId;
                        log.info("[RemoteThreadPoolQueryService] queryPoolMetrics → GET {}", url);
                        String resultStr = HttpUtil.get(url, 5000);
                        JSONObject json = JSON.parseObject(resultStr);
                        if (json != null && json.getJSONObject("data") != null) {
                            return parseMetricsFromJson(json.getJSONObject("data"), inst.getIp() + ":" + inst.getPort());
                        }
                    } catch (Exception e) {
                        log.warn("[RemoteThreadPoolQueryService] failed to query {} from {}:{}",
                                threadPoolId, inst.getIp(), inst.getPort(), e);
                    }
                    return null;
                }, httpPoolExecutor))
                .collect(Collectors.toList());

        for (CompletableFuture<Map<String, Object>> f : futures) {
            try {
                Map<String, Object> m = f.join();
                if (m != null) allMetrics.add(m);
            } catch (Exception ignored) {}
        }

        if (allMetrics.isEmpty()) {
            return null;
        }

        if (allMetrics.size() == 1) {
            return allMetrics.get(0);
        }

        // 多实例聚合
        Map<String, Object> aggregated = new HashMap<>();
        aggregated.put("threadPoolId", threadPoolId);
        aggregated.put("namespace", namespace);
        aggregated.put("serviceName", serviceName);
        aggregated.put("instanceCount", allMetrics.size());
        aggregated.put("instances", allMetrics);
        return aggregated;
    }

    // ─── 调整 ───

    @Override
    public Map<String, Object> adjustPool(String namespace, String serviceName,
                                           String threadPoolId, String instanceId,
                                           Map<String, Object> params) {
        if (instanceId == null || instanceId.isEmpty()) {
            return Map.of("error", "instance_id（实例地址 ip:port）为必填参数");
        }

        // 解析 instanceId，提取 ip:port 部分（兼容 "ip:port" 和 "serviceName:ip:port" 格式）
        String resolvedAddr = instanceId;
        if (instanceId.contains(":")) {
            // 如果格式是 "serviceName:ip:port"，提取最后的 ip:port
            String[] parts = instanceId.split(":");
            if (parts.length >= 3) {
                // serviceName:ip:port 格式
                resolvedAddr = parts[parts.length - 2] + ":" + parts[parts.length - 1];
            }
            // 否则已经是 ip:port 格式，直接使用
        }
        final String targetAddr = resolvedAddr;

        // 直接调用目标实例的调整接口（跳过 Nacos 校验，由调用方确保实例可达）
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            String url = "http://" + targetAddr
                    + "/api/anticipa-dashboard/dynamic/thread-pool/" + threadPoolId + "/adjust";

            Map<String, Object> body = new HashMap<>(params);
            body.put("poolId", threadPoolId);
            String bodyJson = JSON.toJSONString(body);

            log.info("[RemoteThreadPoolQueryService] adjustPool → POST {} body={}", url, bodyJson);
            long startMs = System.currentTimeMillis();

            HttpResponse httpResponse = HttpRequest.post(url)
                    .body(bodyJson, "application/json")
                    .timeout(5000)
                    .execute();
            String resultStr = httpResponse.body();
            int httpStatus = httpResponse.getStatus();
            log.info("[RemoteThreadPoolQueryService] adjustPool ← HTTP {} from {} in {}ms, bodyLen={}",
                    httpStatus, targetAddr, System.currentTimeMillis() - startMs, resultStr.length());

            if (httpStatus != 200) {
                log.warn("[RemoteThreadPoolQueryService] adjust returned HTTP {} from {}, body: {}",
                        httpStatus, targetAddr, resultStr.length() > 200 ? resultStr.substring(0, 200) : resultStr);
                Map<String, Object> failResult = new HashMap<>();
                failResult.put("instanceId", targetAddr);
                failResult.put("success", false);
                failResult.put("error", "远程实例返回 HTTP " + httpStatus + "，请确认实例状态正常且线程池ID和参数正确");
                results.add(failResult);
            } else {
                JSONObject json = JSON.parseObject(resultStr);
                if (json != null) {
                    Map<String, Object> adjustResult = new HashMap<>();
                    adjustResult.put("instanceId", targetAddr);
                    Object code = json.get("code");
                    boolean success = (code instanceof Integer && (Integer) code == 0)
                            || (code instanceof String && "0".equals(code));
                    adjustResult.put("success", success);
                    adjustResult.put("data", json.get("data"));
                    results.add(adjustResult);
                }
            }
        } catch (Exception e) {
            log.warn("[RemoteThreadPoolQueryService] failed to adjust {} on {}",
                    threadPoolId, targetAddr, e);
            Map<String, Object> failResult = new HashMap<>();
            failResult.put("instanceId", targetAddr);
            failResult.put("success", false);
            failResult.put("error", e.getMessage());
            results.add(failResult);
        }

        if (results.isEmpty()) {
            return null;
        }

        Map<String, Object> result = new HashMap<>(results.get(0));
        result.put("threadPoolId", threadPoolId);
        if (namespace != null) result.put("namespace", namespace);
        if (serviceName != null) result.put("serviceName", serviceName);
        return result;
    }

    // ─── 列表 ───

    @Override
    public List<String> listPoolIds() {
        List<InstanceInfo> instances = instanceDiscoveryService.discoverAllInstances();
        if (instances.isEmpty()) {
            return Collections.emptyList();
        }
        // 直接使用已通过 HTTP 获取的 threadPoolIds（由 RemoteInstanceDiscoveryService.fetchPoolIdsFromInstance 填充）
        Set<String> poolIds = new LinkedHashSet<>();
        for (InstanceInfo inst : instances) {
            if (inst.getThreadPoolIds() != null) {
                poolIds.addAll(inst.getThreadPoolIds());
            }
        }
        return new ArrayList<>(poolIds);
    }

    @Override
    public List<String> listPoolIdsByService(String serviceName) {
        if (serviceName == null || serviceName.isEmpty()) {
            return Collections.emptyList();
        }
        List<InstanceInfo> instances = instanceDiscoveryService.discoverInstances(serviceName);
        if (instances.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> poolIds = new LinkedHashSet<>();
        for (InstanceInfo inst : instances) {
            if (inst.getThreadPoolIds() != null) {
                poolIds.addAll(inst.getThreadPoolIds());
            }
        }
        return new ArrayList<>(poolIds);
    }

    @Override
    public List<String> listPoolIdsByService(String namespace, String serviceName) {
        if (namespace == null || namespace.isEmpty() || serviceName == null || serviceName.isEmpty()) {
            return Collections.emptyList();
        }
        // 通过 Nacos 发现该命名空间下的服务实例
        List<NacosHttpClient.NacosInstanceInfo> instances = nacosHttpClient.listInstances(namespace, serviceName);
        if (instances.isEmpty()) {
            return Collections.emptyList();
        }
        // 并行调用各实例的 /snapshot 端点，获取线程池 ID 列表
        Set<String> poolIds = new LinkedHashSet<>();
        List<CompletableFuture<List<String>>> futures = instances.stream()
                .map(inst -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String url = "http://" + inst.getIp() + ":" + inst.getPort()
                                + "/api/anticipa-dashboard/dynamic/thread-pool/snapshot";
                        String resultStr = HttpUtil.get(url, 5000);
                        JSONObject json = JSON.parseObject(resultStr);
                        if (json != null && json.getJSONObject("data") != null) {
                            Object threadPoolsObj = json.getJSONObject("data").get("threadPools");
                            if (threadPoolsObj instanceof List) {
                                List<?> threadPools = (List<?>) threadPoolsObj;
                                List<String> ids = new ArrayList<>();
                                for (Object item : threadPools) {
                                    if (item instanceof Map) {
                                        Object poolId = ((Map<?, ?>) item).get("poolId");
                                        if (poolId != null) ids.add(poolId.toString());
                                    }
                                }
                                return ids;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[RemoteThreadPoolQueryService] failed to fetch pool ids from {}:{}",
                                inst.getIp(), inst.getPort(), e);
                    }
                    return Collections.<String>emptyList();
                }, httpPoolExecutor))
                .collect(Collectors.toList());

        for (CompletableFuture<List<String>> f : futures) {
            try {
                poolIds.addAll(f.join());
            } catch (Exception ignored) {}
        }
        return new ArrayList<>(poolIds);
    }

    @Override
    public List<Map<String, Object>> listInstancePoolDetails(String namespace, String serviceName, String instanceId) {
        if (namespace == null || namespace.isEmpty() || serviceName == null || serviceName.isEmpty()
                || instanceId == null || instanceId.isEmpty()) {
            return Collections.emptyList();
        }

        // 通过 Nacos 验证并定位实例
        List<NacosHttpClient.NacosInstanceInfo> instances = nacosHttpClient.listInstances(namespace, serviceName);
        if (instances.isEmpty()) {
            return Collections.emptyList();
        }

        // 匹配目标实例（支持 "ip:port" 和 "serviceName:ip:port" 格式）
        NacosHttpClient.NacosInstanceInfo targetInstance = null;
        for (NacosHttpClient.NacosInstanceInfo inst : instances) {
            String ipPort = inst.getIp() + ":" + inst.getPort();
            if (ipPort.equals(instanceId) || (serviceName + ":" + ipPort).equals(instanceId)) {
                targetInstance = inst;
                break;
            }
        }
        if (targetInstance == null) {
            return Collections.emptyList();
        }

        // 调用目标实例的 /snapshot 端点获取线程池详情
        try {
            String url = "http://" + targetInstance.getIp() + ":" + targetInstance.getPort()
                    + "/api/anticipa-dashboard/dynamic/thread-pool/snapshot";
            String resultStr = HttpUtil.get(url, 5000);
            JSONObject json = JSON.parseObject(resultStr);
            if (json == null || json.getJSONObject("data") == null) {
                return Collections.emptyList();
            }

            Object threadPoolsObj = json.getJSONObject("data").get("threadPools");
            if (!(threadPoolsObj instanceof List)) {
                return Collections.emptyList();
            }

            List<?> threadPools = (List<?>) threadPoolsObj;
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : threadPools) {
                if (item instanceof Map) {
                    Map<String, Object> poolDetail = new HashMap<>((Map<String, Object>) item);
                    poolDetail.put("instanceId", targetInstance.getIp() + ":" + targetInstance.getPort());
                    poolDetail.put("namespace", namespace);
                    poolDetail.put("serviceName", serviceName);
                    result.add(poolDetail);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[RemoteThreadPoolQueryService] failed to fetch pool details from {}:{}",
                    targetInstance.getIp(), targetInstance.getPort(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> listNacosConfigs(String namespace) {
        List<NacosHttpClient.NacosConfigInfo> configs = nacosHttpClient.listConfigs(namespace, 1, 100);
        if (configs.isEmpty()) {
            return Collections.emptyList();
        }
        return configs.stream()
                .map(NacosHttpClient.NacosConfigInfo::getDataId)
                .collect(Collectors.toList());
    }

    // ─── 配置修改 ───

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateConfig(String namespace, String dataId, String group,
                                             String threadPoolId, Map<String, Object> params) {
        try {
            // 1. 参数校验
            if (namespace == null || namespace.isEmpty()) {
                return Map.of("error", "namespace 不能为空");
            }
            if (dataId == null || dataId.isEmpty()) {
                return Map.of("error", "dataId 不能为空");
            }
            if (threadPoolId == null || threadPoolId.isEmpty()) {
                return Map.of("error", "threadPoolId 不能为空");
            }
            if (group == null || group.isEmpty()) {
                group = "DEFAULT_GROUP";
            }

            // 2. 读取当前 Nacos 配置
            NacosHttpClient.NacosConfigDetail configDetail = nacosHttpClient.getConfig(namespace, dataId, group);
            if (configDetail == null || configDetail.getContent() == null) {
                return Map.of("error", "未找到 Nacos 配置: namespace=" + namespace + ", dataId=" + dataId);
            }

            // 3. 解析 YAML 为 Map
            Yaml yaml = new Yaml();
            Map<String, Object> rootMap = yaml.load(configDetail.getContent());
            if (rootMap == null || rootMap.isEmpty()) {
                return Map.of("error", "配置文件内容为空或格式不正确");
            }

            // 4. 查找配置前缀 (anticipa / onethread)
            String matchedPrefix = null;
            Map<String, Object> executorMap = null;
            for (String prefix : new String[]{"anticipa", "onethread"}) {
                Object val = rootMap.get(prefix);
                if (val instanceof Map) {
                    Object executors = ((Map<String, Object>) val).get("executors");
                    if (executors instanceof List) {
                        matchedPrefix = prefix;
                        executorMap = (Map<String, Object>) val;
                        break;
                    }
                }
            }
            if (matchedPrefix == null) {
                return Map.of("error", "无法解析配置文件内容，未找到 anticipa.executors 或 onethread.executors 配置节");
            }

            List<Object> executors = (List<Object>) executorMap.get("executors");
            if (executors == null || executors.isEmpty()) {
                return Map.of("error", "配置文件中未找到线程池定义");
            }

            // 5. 查找目标线程池
            // YAML 配置文件使用 kebab-case（如 thread-pool-id），SnakeYAML 解析后保持原格式
            Map<String, Object> targetPool = null;
            for (Object executor : executors) {
                if (executor instanceof Map) {
                    Map<String, Object> poolDef = (Map<String, Object>) executor;
                    // 兼容 kebab-case 和 camelCase 两种 key 格式
                    Object poolIdVal = poolDef.getOrDefault("thread-pool-id", poolDef.get("threadPoolId"));
                    if (threadPoolId.equals(String.valueOf(poolIdVal))) {
                        targetPool = poolDef;
                        break;
                    }
                }
            }
            if (targetPool == null) {
                return Map.of("error", "在配置文件中未找到线程池: " + threadPoolId);
            }

            // 6. 记录 before 快照（兼容 kebab-case 和 camelCase）
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("corePoolSize", targetPool.getOrDefault("core-pool-size", targetPool.get("corePoolSize")));
            before.put("maximumPoolSize", targetPool.getOrDefault("maximum-pool-size", targetPool.get("maximumPoolSize")));
            before.put("queueCapacity", targetPool.getOrDefault("queue-capacity", targetPool.get("queueCapacity")));
            before.put("keepAliveTime", targetPool.getOrDefault("keep-alive-time", targetPool.get("keepAliveTime")));

            // 7. 应用参数修改（使用 YAML 中的 kebab-case key）
            Map<String, Object> changes = new LinkedHashMap<>();
            if (params.containsKey("corePoolSize") && params.get("corePoolSize") != null) {
                int val = ((Number) params.get("corePoolSize")).intValue();
                // 使用已存在的 key 格式，如果不存在则用 kebab-case
                String key = targetPool.containsKey("core-pool-size") ? "core-pool-size" : "corePoolSize";
                targetPool.put(key, val);
                changes.put("corePoolSize", val);
            }
            if (params.containsKey("maximumPoolSize") && params.get("maximumPoolSize") != null) {
                int val = ((Number) params.get("maximumPoolSize")).intValue();
                String key = targetPool.containsKey("maximum-pool-size") ? "maximum-pool-size" : "maximumPoolSize";
                targetPool.put(key, val);
                changes.put("maximumPoolSize", val);
            }
            if (params.containsKey("queueCapacity") && params.get("queueCapacity") != null) {
                int val = ((Number) params.get("queueCapacity")).intValue();
                String key = targetPool.containsKey("queue-capacity") ? "queue-capacity" : "queueCapacity";
                targetPool.put(key, val);
                changes.put("queueCapacity", val);
            }
            if (params.containsKey("keepAliveTime") && params.get("keepAliveTime") != null) {
                int val = ((Number) params.get("keepAliveTime")).intValue();
                String key = targetPool.containsKey("keep-alive-time") ? "keep-alive-time" : "keepAliveTime";
                targetPool.put(key, val);
                changes.put("keepAliveTime", val);
            }

            if (changes.isEmpty()) {
                return Map.of("error", "未指定要修改的参数");
            }

            // 8. 记录 after 快照（兼容 kebab-case 和 camelCase）
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("corePoolSize", targetPool.getOrDefault("core-pool-size", targetPool.get("corePoolSize")));
            after.put("maximumPoolSize", targetPool.getOrDefault("maximum-pool-size", targetPool.get("maximumPoolSize")));
            after.put("queueCapacity", targetPool.getOrDefault("queue-capacity", targetPool.get("queueCapacity")));
            after.put("keepAliveTime", targetPool.getOrDefault("keep-alive-time", targetPool.get("keepAliveTime")));

            // 9. 序列化回 YAML
            YAMLFactory factory = new YAMLFactory();
            factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
            factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);

            ObjectMapper objectMapper = new ObjectMapper(factory);
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);

            String yamlStr = objectMapper.writeValueAsString(Collections.singletonMap(matchedPrefix, executorMap));

            // 10. 发布到 Nacos
            boolean published = nacosHttpClient.publishConfig(
                    namespace, dataId, group,
                    configDetail.getAppName(), configDetail.getId(), configDetail.getMd5(),
                    yamlStr, "yaml");
            if (!published) {
                return Map.of("error", "配置发布到 Nacos 失败");
            }

            // 11. 获取影响实例数
            int affectedInstanceCount = 0;
            try {
                String appName = configDetail.getAppName();
                if (appName != null && !appName.isEmpty()) {
                    List<NacosHttpClient.NacosInstanceInfo> instances =
                            nacosHttpClient.listInstances(namespace, appName);
                    affectedInstanceCount = instances != null ? instances.size() : 0;
                }
            } catch (Exception e) {
                log.warn("[RemoteThreadPoolQueryService] failed to get instance count for app={}",
                        configDetail.getAppName(), e);
            }

            // 12. 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("threadPoolId", threadPoolId);
            result.put("namespace", namespace);
            result.put("dataId", dataId);
            result.put("group", group);
            result.put("before", before);
            result.put("after", after);
            result.put("changes", changes);
            result.put("affectedInstanceCount", affectedInstanceCount);
            result.put("configModified", true);
            return result;

        } catch (Exception e) {
            log.error("[RemoteThreadPoolQueryService] updateConfig failed for ns={}, dataId={}, pool={}",
                    namespace, dataId, threadPoolId, e);
            return Map.of("error", "配置更新失败: " + e.getMessage());
        }
    }

    // ─── 日志查询 ───

    @Override
    public Map<String, Object> checkLogsExist(String namespace, String serviceName,
                                                String threadPoolId, String instanceId) {
        if (namespace == null || namespace.isEmpty() || serviceName == null || serviceName.isEmpty()) {
            return Map.of("exists", false, "threadPoolId", threadPoolId, "error", "namespace and serviceName are required");
        }

        List<NacosHttpClient.NacosInstanceInfo> instances = nacosHttpClient.listInstances(namespace, serviceName);
        if (instances.isEmpty()) {
            return Map.of("exists", false, "threadPoolId", threadPoolId, "error", "No instances found");
        }

        // 如果指定了 instanceId，过滤
        if (instanceId != null && !instanceId.isEmpty()) {
            instances = instances.stream()
                    .filter(s -> {
                        String ipPort = s.getIp() + ":" + s.getPort();
                        return ipPort.equals(instanceId) || (serviceName + ":" + ipPort).equals(instanceId);
                    })
                    .collect(Collectors.toList());
            if (instances.isEmpty()) {
                return Map.of("exists", false, "threadPoolId", threadPoolId, "error", "Instance not found: " + instanceId);
            }
        }

        // 查询第一个可用实例的日志存在性
        for (NacosHttpClient.NacosInstanceInfo inst : instances) {
            try {
                String url = "http://" + inst.getIp() + ":" + inst.getPort()
                        + "/api/anticipa-dashboard/logs/" + threadPoolId + "/exists";
                String resultStr = HttpUtil.get(url, 5000);
                JSONObject json = JSON.parseObject(resultStr);
                if (json != null && json.getJSONObject("data") != null) {
                    JSONObject data = json.getJSONObject("data");
                    boolean exists = data.getBooleanValue("exists");
                    Map<String, Object> result = new HashMap<>();
                    result.put("exists", exists);
                    result.put("threadPoolId", threadPoolId);
                    result.put("instanceId", inst.getIp() + ":" + inst.getPort());
                    return result;
                }
            } catch (Exception e) {
                log.warn("[RemoteThreadPoolQueryService] failed to check logs exist for {} from {}:{}",
                        threadPoolId, inst.getIp(), inst.getPort(), e);
            }
        }

        return Map.of("exists", false, "threadPoolId", threadPoolId, "error", "Failed to check logs on all instances");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> queryHistory(String namespace, String serviceName, String threadPoolId,
                                             String instanceId, String startTime, String endTime,
                                             String aggregation, int limit) {
        if (instanceId == null || instanceId.isEmpty()) {
            return Map.of("threadPoolId", threadPoolId, "recordCount", 0, "error", "instanceId is required");
        }

        String addr = resolveTargetAddr(instanceId);
        try {
            String baseUrl = "http://" + addr + "/api/anticipa-dashboard/logs/" + threadPoolId;
            Map<String, String> queryParams = new LinkedHashMap<>();
            if (startTime != null && !startTime.isEmpty()) queryParams.put("startTime", startTime);
            if (endTime != null && !endTime.isEmpty()) queryParams.put("endTime", endTime);
            if (aggregation != null && !aggregation.isEmpty()) queryParams.put("aggregation", aggregation);
            if (limit > 0) queryParams.put("limit", String.valueOf(limit));

            String resultStr = httpGet(baseUrl, queryParams, 10000, "queryHistory");
            if (resultStr == null) {
                return Map.of("threadPoolId", threadPoolId, "recordCount", 0, "records", Collections.emptyList(),
                        "error", "远程实例请求失败");
            }
            JSONObject json = JSON.parseObject(resultStr);
            if (json != null && json.get("data") != null) {
                Object dataObj = json.get("data");
                Map<String, Object> result = new HashMap<>();
                result.put("instanceId", addr);
                result.put("threadPoolId", threadPoolId);
                if (namespace != null) result.put("namespace", namespace);
                if (serviceName != null) result.put("serviceName", serviceName);
                if (dataObj instanceof List) {
                    List<?> records = (List<?>) dataObj;
                    result.put("recordCount", records.size());
                    result.put("records", records);
                } else {
                    result.put("recordCount", 0);
                    result.put("records", Collections.emptyList());
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("[RemoteThreadPoolQueryService] queryHistory failed for {} on {}",
                    threadPoolId, addr, e);
        }
        return Map.of("threadPoolId", threadPoolId, "recordCount", 0, "records", Collections.emptyList());
    }

    @Override
    public Map<String, Object> analyzeTrends(String namespace, String serviceName, String threadPoolId,
                                              String instanceId, String startTime, String endTime) {
        if (namespace == null || namespace.isEmpty() || serviceName == null || serviceName.isEmpty()) {
            return Map.of("threadPoolId", threadPoolId, "recordCount", 0, "error", "namespace and serviceName are required");
        }

        List<NacosHttpClient.NacosInstanceInfo> instances = nacosHttpClient.listInstances(namespace, serviceName);
        if (instances.isEmpty()) {
            return Map.of("threadPoolId", threadPoolId, "recordCount", 0, "error", "No instances found");
        }

        // 如果指定了 instanceId，过滤
        if (instanceId != null && !instanceId.isEmpty()) {
            instances = instances.stream()
                    .filter(s -> {
                        String ipPort = s.getIp() + ":" + s.getPort();
                        return ipPort.equals(instanceId) || (serviceName + ":" + ipPort).equals(instanceId);
                    })
                    .collect(Collectors.toList());
            if (instances.isEmpty()) {
                return Map.of("threadPoolId", threadPoolId, "recordCount", 0, "error", "Instance not found: " + instanceId);
            }
        }

        // 查询第一个可用实例的摘要统计
        for (NacosHttpClient.NacosInstanceInfo inst : instances) {
            try {
                String baseUrl = "http://" + inst.getIp() + ":" + inst.getPort()
                        + "/api/anticipa-dashboard/logs/" + threadPoolId + "/summary";
                Map<String, String> queryParams = new LinkedHashMap<>();
                if (startTime != null && !startTime.isEmpty()) queryParams.put("startTime", startTime);
                if (endTime != null && !endTime.isEmpty()) queryParams.put("endTime", endTime);

                String resultStr = httpGet(baseUrl, queryParams, 10000, "analyzeTrends");
                if (resultStr == null) {
                    continue;
                }
                JSONObject json = JSON.parseObject(resultStr);
                if (json != null && json.getJSONObject("data") != null) {
                    Map<String, Object> result = new HashMap<>(json.getJSONObject("data"));
                    result.put("instanceId", inst.getIp() + ":" + inst.getPort());
                    return result;
                }
            } catch (Exception e) {
                log.warn("[RemoteThreadPoolQueryService] failed to analyze trends for {} from {}:{}",
                        threadPoolId, inst.getIp(), inst.getPort(), e);
            }
        }

        return Map.of("threadPoolId", threadPoolId, "recordCount", 0, "error", "Failed to get trend analysis from all instances");
    }

    // ─── 直调模式（跳过 Nacos 发现，直接用 instanceId 调用目标实例） ───

    @Override
    public Map<String, Object> queryPoolMetricsDirect(String threadPoolId, String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) {
            log.warn("[RemoteThreadPoolQueryService] queryPoolMetricsDirect: instanceId is required");
            return null;
        }
        String addr = resolveTargetAddr(instanceId);
        try {
            String url = "http://" + addr + "/api/anticipa-dashboard/dynamic/thread-pool/" + threadPoolId;
            log.info("[RemoteThreadPoolQueryService] queryPoolMetricsDirect → GET {}", url);
            String resultStr = HttpUtil.get(url, 5000);
            JSONObject json = JSON.parseObject(resultStr);
            if (json != null && json.getJSONObject("data") != null) {
                return parseMetricsFromJson(json.getJSONObject("data"), addr);
            }
        } catch (Exception e) {
            log.warn("[RemoteThreadPoolQueryService] queryPoolMetricsDirect failed for {} on {}",
                    threadPoolId, addr, e);
        }
        return null;
    }

    @Override
    public Map<String, Object> queryHistoryDirect(String threadPoolId, String instanceId,
                                                   String startTime, String endTime,
                                                   String aggregation, int limit) {
        if (instanceId == null || instanceId.isEmpty()) {
            return Map.of("threadPoolId", threadPoolId, "recordCount", 0, "error", "instanceId is required");
        }
        String addr = resolveTargetAddr(instanceId);
        try {
            String baseUrl = "http://" + addr + "/api/anticipa-dashboard/logs/" + threadPoolId;
            Map<String, String> queryParams = new LinkedHashMap<>();
            if (startTime != null && !startTime.isEmpty()) queryParams.put("startTime", startTime);
            if (endTime != null && !endTime.isEmpty()) queryParams.put("endTime", endTime);
            if (aggregation != null && !aggregation.isEmpty()) queryParams.put("aggregation", aggregation);
            if (limit > 0) queryParams.put("limit", String.valueOf(limit));

            String resultStr = httpGet(baseUrl, queryParams, 50000, "queryHistoryDirect");
            if (resultStr == null) {
                return Map.of("threadPoolId", threadPoolId, "recordCount", 0, "records", Collections.emptyList(),
                        "error", "远程实例请求失败");
            }
            JSONObject json = JSON.parseObject(resultStr);
            if (json != null && json.get("data") != null) {
                Object dataObj = json.get("data");
                Map<String, Object> result = new HashMap<>();
                result.put("instanceId", addr);
                result.put("threadPoolId", threadPoolId);
                if (dataObj instanceof List) {
                    List<?> records = (List<?>) dataObj;
                    result.put("recordCount", records.size());
                    result.put("records", records);
                } else {
                    result.put("recordCount", 0);
                    result.put("records", Collections.emptyList());
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("[RemoteThreadPoolQueryService] queryHistoryDirect failed for {} on {}",
                    threadPoolId, addr, e);
        }
        return Map.of("threadPoolId", threadPoolId, "recordCount", 0, "records", Collections.emptyList());
    }

    @Override
    public Map<String, Object> checkLogsExistDirect(String threadPoolId, String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) {
            return Map.of("exists", false, "threadPoolId", threadPoolId, "error", "instanceId is required");
        }
        String addr = resolveTargetAddr(instanceId);
        try {
            String url = "http://" + addr + "/api/anticipa-dashboard/logs/" + threadPoolId + "/exists";
            log.info("[RemoteThreadPoolQueryService] checkLogsExistDirect → GET {}", url);
            String resultStr = HttpUtil.get(url, 10000);
            JSONObject json = JSON.parseObject(resultStr);
            if (json != null && json.getJSONObject("data") != null) {
                JSONObject data = json.getJSONObject("data");
                boolean exists = data.getBooleanValue("exists");
                Map<String, Object> result = new HashMap<>();
                result.put("exists", exists);
                result.put("threadPoolId", threadPoolId);
                result.put("instanceId", addr);
                return result;
            }
        } catch (Exception e) {
            log.warn("[RemoteThreadPoolQueryService] checkLogsExistDirect failed for {} on {}",
                    threadPoolId, addr, e);
        }
        return Map.of("exists", false, "threadPoolId", threadPoolId, "error", "Failed to check logs on " + addr);
    }

    @Override
    public Map<String, Object> analyzeTrendsDirect(String threadPoolId, String instanceId,
                                                    String startTime, String endTime) {
        if (instanceId == null || instanceId.isEmpty()) {
            return Map.of("threadPoolId", threadPoolId, "recordCount", 0, "error", "instanceId is required");
        }
        String addr = resolveTargetAddr(instanceId);
        try {
            String baseUrl = "http://" + addr + "/api/anticipa-dashboard/logs/" + threadPoolId + "/summary";
            Map<String, String> queryParams = new LinkedHashMap<>();
            if (startTime != null && !startTime.isEmpty()) queryParams.put("startTime", startTime);
            if (endTime != null && !endTime.isEmpty()) queryParams.put("endTime", endTime);

            String resultStr = httpGet(baseUrl, queryParams, 10000, "analyzeTrendsDirect");
            if (resultStr == null) {
                return Map.of("threadPoolId", threadPoolId, "recordCount", 0,
                        "error", "远程实例请求失败");
            }
            JSONObject json = JSON.parseObject(resultStr);
            if (json != null && json.getJSONObject("data") != null) {
                Map<String, Object> result = new HashMap<>(json.getJSONObject("data"));
                result.put("instanceId", addr);
                return result;
            }
        } catch (Exception e) {
            log.warn("[RemoteThreadPoolQueryService] analyzeTrendsDirect failed for {} on {}",
                    threadPoolId, addr, e);
        }
        return Map.of("threadPoolId", threadPoolId, "recordCount", 0, "error", "Failed to get trend analysis from " + addr);
    }

    // ─── 内部辅助 ───

    /**
     * 执行 GET 请求并返回响应体；非 200 时返回 null 并打 warn 日志。
     * <p>
     * 查询参数通过 {@link URLEncoder#encode} 手动编码后拼接到 URL，
     * 避免依赖 Hutool 的 .form()（5.x 对 GET 请求不追加 query string）。
     * </p>
     *
     * @param baseUrl 不含查询参数的基础 URL
     * @param params  查询参数（值不会被二次编码）
     * @param timeoutMs 超时毫秒
     * @param logLabel 日志标识，如 "queryHistoryDirect"
     * @return HTTP 200 时的响应体字符串；非 200 或异常时返回 null
     */
    private String httpGet(String baseUrl, Map<String, String> params, int timeoutMs, String logLabel) {
        try {
            StringBuilder urlBuilder = new StringBuilder(baseUrl);
            if (params != null && !params.isEmpty()) {
                List<String> pairs = new ArrayList<>(params.size());
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    pairs.add(entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                }
                urlBuilder.append("?").append(String.join("&", pairs));
            }
            String fullUrl = urlBuilder.toString();
            log.info("[RemoteThreadPoolQueryService] {} → GET {}", logLabel, fullUrl);

            HttpResponse httpResponse = HttpRequest.get(fullUrl)
                    .timeout(timeoutMs)
                    .execute();
            String body = httpResponse.body();
            if (httpResponse.getStatus() != 200) {
                log.warn("[RemoteThreadPoolQueryService] {} ← HTTP {}, bodyLen={}",
                        logLabel, httpResponse.getStatus(), body.length());
                return null;
            }
            return body;
        } catch (Exception e) {
            log.warn("[RemoteThreadPoolQueryService] {} failed", logLabel, e);
            return null;
        }
    }

    /**
     * 解析 instanceId 为 ip:port 地址。
     * 兼容 "ip:port" 和 "serviceName:ip:port" 两种格式。
     */
    private String resolveTargetAddr(String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) {
            return instanceId;
        }
        if (instanceId.contains(":")) {
            String[] parts = instanceId.split(":");
            if (parts.length >= 3) {
                // serviceName:ip:port 格式，提取最后的 ip:port
                return parts[parts.length - 2] + ":" + parts[parts.length - 1];
            }
            // 已经是 ip:port 格式，直接返回
        }
        return instanceId;
    }

    private Map<String, Object> parseMetricsFromJson(JSONObject data, String instanceId) {
        Map<String, Object> result = new HashMap<>();
        result.put("threadPoolId", data.getString("threadPoolId"));
        result.put("instanceId", instanceId);
        result.put("corePoolSize", data.getIntValue("corePoolSize"));
        result.put("maximumPoolSize", data.getIntValue("maximumPoolSize"));
        result.put("activeCount", data.getIntValue("activeCount"));
        result.put("poolSize", data.getIntValue("poolSize"));
        result.put("largestPoolSize", data.getIntValue("largestPoolSize"));
        result.put("queueSize", data.getIntValue("queueSize"));
        result.put("queueCapacity", data.getIntValue("queueSize") + data.getIntValue("queueRemainingCapacity"));
        result.put("queueRemainingCapacity", data.getIntValue("queueRemainingCapacity"));
        result.put("completedTaskCount", data.getLongValue("completedTaskCount"));
        result.put("taskCount", data.getLongValue("taskCount"));
        result.put("keepAliveSeconds", data.getIntValue("keepAliveTime"));
        result.put("rejectedHandler", data.getString("rejectedExecutionHandler"));

        int queueSize = data.getIntValue("queueSize");
        int queueCap = queueSize + data.getIntValue("queueRemainingCapacity");
        int queueUsage = queueCap > 0 ? queueSize * 100 / queueCap : 0;
        result.put("queueUsagePercent", queueUsage);
        return result;
    }
}

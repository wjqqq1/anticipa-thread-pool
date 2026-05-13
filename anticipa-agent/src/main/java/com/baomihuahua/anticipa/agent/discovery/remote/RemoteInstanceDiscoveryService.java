package com.baomihuahua.anticipa.agent.discovery.remote;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomihuahua.anticipa.agent.config.AgentProperties;
import com.baomihuahua.anticipa.agent.discovery.InstanceDiscoveryService;
import com.baomihuahua.anticipa.agent.discovery.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 基于 Nacos 的远程实例发现服务。
 * <p>
 * 通过 Nacos Open API 发现所有注册的客户端实例，
 * 并通过 HTTP 调用获取各实例的线程池列表。
 * </p>
 */
public class RemoteInstanceDiscoveryService implements InstanceDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(RemoteInstanceDiscoveryService.class);

    private final NacosHttpClient nacosHttpClient;
    private final AgentProperties.NacosConfig nacosConfig;

    public RemoteInstanceDiscoveryService(NacosHttpClient nacosHttpClient,
                                           AgentProperties.NacosConfig nacosConfig) {
        this.nacosHttpClient = nacosHttpClient;
        this.nacosConfig = nacosConfig;
    }

    @Override
    public List<String> listNamespaces() {
        List<String> namespaces = nacosConfig.getNamespaces();
        return namespaces != null ? namespaces : Collections.emptyList();
    }

    @Override
    public List<String> listServiceNames(String namespace) {
        return nacosHttpClient.listServiceNames(namespace);
    }

    @Override
    public List<InstanceInfo> discoverInstances(String namespace, String serviceName) {
        List<NacosHttpClient.NacosInstanceInfo> instances = nacosHttpClient.listInstances(namespace, serviceName);
        if (instances.isEmpty()) {
            return Collections.emptyList();
        }

        List<InstanceInfo> result = new ArrayList<>();
        for (NacosHttpClient.NacosInstanceInfo inst : instances) {
            try {
                InstanceInfo info = new InstanceInfo();
                info.setInstanceId(serviceName + ":" + inst.getIp() + ":" + inst.getPort());
                info.setAppName(serviceName);
                info.setNamespace(namespace);
                info.setHost(inst.getIp());
                info.setPort(String.valueOf(inst.getPort()));
                info.setStatus(inst.isHealthy() ? "ONLINE" : "OFFLINE");

                List<String> poolIds = fetchPoolIdsFromInstance(inst.getIp(), inst.getPort());
                info.setThreadPoolIds(poolIds);

                result.add(info);
            } catch (Exception e) {
                log.warn("[RemoteInstanceDiscovery] failed to get pool list from {}:{}",
                        inst.getIp(), inst.getPort(), e);
            }
        }

        return result;
    }

    @Override
    public List<InstanceInfo> discoverInstances(String serviceName) {
        List<String> namespaces = nacosConfig.getNamespaces();
        if (namespaces == null || namespaces.isEmpty()) {
            return Collections.emptyList();
        }

        List<InstanceInfo> allInstances = new ArrayList<>();
        for (String namespace : namespaces) {
            try {
                allInstances.addAll(discoverInstances(namespace, serviceName));
            } catch (Exception e) {
                log.warn("[RemoteInstanceDiscovery] failed for service: {} in namespace: {}",
                        serviceName, namespace, e);
            }
        }
        return allInstances;
    }

    @Override
    public List<InstanceInfo> discoverAllInstances() {
        List<String> namespaces = nacosConfig.getNamespaces();
        if (namespaces == null || namespaces.isEmpty()) {
            return Collections.emptyList();
        }

        List<InstanceInfo> allInstances = new ArrayList<>();
        for (String namespace : namespaces) {
            try {
                List<String> serviceNames = nacosHttpClient.listServiceNames(namespace);
                if (serviceNames == null || serviceNames.isEmpty()) {
                    continue;
                }
                for (String serviceName : serviceNames) {
                    try {
                        allInstances.addAll(discoverInstances(namespace, serviceName));
                    } catch (Exception e) {
                        log.warn("[RemoteInstanceDiscovery] failed for service: {} in namespace: {}",
                                serviceName, namespace, e);
                    }
                }
            } catch (Exception e) {
                log.warn("[RemoteInstanceDiscovery] failed to query namespace: {}", namespace, e);
            }
        }
        return allInstances;
    }

    /**
     * 调用客户端实例的 /api/anticipa-dashboard/dynamic/thread-pool/snapshot 接口获取线程池列表。
     * <p>
     * 注意：/instance/info 端点返回的 InstanceInfoRespDTO 不包含 threadPoolIds 字段，
     * 必须使用 /snapshot 端点，其返回的 InstanceSnapshotDTO 中包含 threadPools 列表。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private List<String> fetchPoolIdsFromInstance(String ip, int port) {
        try {
            String url = "http://" + ip + ":" + port + "/api/anticipa-dashboard/dynamic/thread-pool/snapshot";
            String resultStr = HttpUtil.get(url, 5000);
            JSONObject json = JSON.parseObject(resultStr);
            if (json == null) return Collections.emptyList();

            Object data = json.get("data");
            if (data instanceof JSONObject) {
                Object threadPoolsObj = ((JSONObject) data).get("threadPools");
                if (threadPoolsObj instanceof List) {
                    List<Map<String, Object>> threadPools = (List<Map<String, Object>>) threadPoolsObj;
                    List<String> poolIds = new ArrayList<>();
                    for (Map<String, Object> pool : threadPools) {
                        Object poolId = pool.get("poolId");
                        if (poolId != null) {
                            poolIds.add(poolId.toString());
                        }
                    }
                    return poolIds;
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.debug("[RemoteInstanceDiscovery] failed to fetch pool ids from {}:{}: {}",
                    ip, port, e.getMessage());
            return Collections.emptyList();
        }
    }
}

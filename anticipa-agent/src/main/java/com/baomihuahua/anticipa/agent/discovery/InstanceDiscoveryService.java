package com.baomihuahua.anticipa.agent.discovery;

import java.util.List;

/**
 * 实例发现服务。
 * <p>
 * 用于发现运行中的服务实例及其上的线程池列表。
 * 默认实现返回本地实例信息，Nacos 环境下可替换为远程发现。
 * </p>
 */
public interface InstanceDiscoveryService {

    /**
     * 获取所有已知实例及其线程池列表。
     */
    List<InstanceInfo> discoverAllInstances();

    /**
     * 按服务名查询实例及其线程池列表。
     * <p>
     * 相比全量查询，按服务名查询更精准高效，避免全量扫描。
     * </p>
     *
     * @param serviceName 服务名称
     * @return 该服务下的实例列表
     */
    default List<InstanceInfo> discoverInstances(String serviceName) {
        return discoverAllInstances().stream()
                .filter(inst -> serviceName.equals(inst.getAppName()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 按命名空间和服务名查询实例及其线程池列表。
     *
     * @param namespace   Nacos 命名空间
     * @param serviceName 服务名称
     * @return 该命名空间下该服务的实例列表
     */
    default List<InstanceInfo> discoverInstances(String namespace, String serviceName) {
        return discoverInstances(serviceName).stream()
                .filter(inst -> namespace == null || namespace.equals(inst.getNamespace()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 返回当前可用的 Nacos 命名空间列表。
     * <p>
     * 本地模式默认返回空列表，远程模式由 Nacos 配置提供。
     * </p>
     */
    default List<String> listNamespaces() {
        return java.util.Collections.emptyList();
    }

    /**
     * 列出指定命名空间下注册的所有服务名。
     *
     * @param namespace Nacos 命名空间
     * @return 服务名列表
     */
    default List<String> listServiceNames(String namespace) {
        return java.util.Collections.emptyList();
    }

    /**
     * 判断是否为多实例环境。
     */
    default boolean isMultiInstance() {
        return discoverAllInstances().size() > 1;
    }
}

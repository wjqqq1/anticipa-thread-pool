package com.baomihuahua.anticipa.agent.discovery;

import java.util.List;

/**
 * 实例信息。
 * <p>
 * 表示一个运行中的服务实例及其上的所有线程池。
 * </p>
 */
public class InstanceInfo {

    /** 实例唯一标识，格式 "appName:host:port" */
    private String instanceId;

    /** 应用名称 */
    private String appName;

    /** Nacos 命名空间 */
    private String namespace;

    /** 主机 IP */
    private String host;

    /** 端口 */
    private String port;

    /** 当前实例上的所有线程池 ID 列表 */
    private List<String> threadPoolIds;

    /** 状态：ONLINE / OFFLINE */
    private String status;

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getPort() { return port; }
    public void setPort(String port) { this.port = port; }
    public List<String> getThreadPoolIds() { return threadPoolIds; }
    public void setThreadPoolIds(List<String> threadPoolIds) { this.threadPoolIds = threadPoolIds; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

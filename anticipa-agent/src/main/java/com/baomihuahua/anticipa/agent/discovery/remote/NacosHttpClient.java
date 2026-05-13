package com.baomihuahua.anticipa.agent.discovery.remote;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomihuahua.anticipa.agent.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自包含的 Nacos Open API 客户端。
 * <p>
 * 参考 dashboard-dev 的 NacosProxyClient，但使用 AgentProperties.NacosConfig，
 * 不依赖 dashboard-dev 的任何类。所有方法均为同步调用，失败时返回空集合/null。
 * </p>
 */
public class NacosHttpClient {

    private static final Logger log = LoggerFactory.getLogger(NacosHttpClient.class);

    private final AgentProperties.NacosConfig nacosConfig;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String NACOS_LOGIN_PATH = "/nacos/v1/auth/login";
    private static final String NACOS_CONFIG_LIST_PATH = "/nacos/v1/cs/configs";
    private static final String NACOS_SERVICE_LIST_PATH = "/nacos/v1/ns/service/list";
    private static final String NACOS_SERVICE_INSTANCE_PATH = "/nacos/v1/ns/instance/list";

    private final Map<String, String> accessTokenCache = new ConcurrentHashMap<>();

    public NacosHttpClient(AgentProperties.NacosConfig nacosConfig) {
        this.nacosConfig = nacosConfig;
    }

    /**
     * 刷新缓存的 access token。
     */
    public void refreshToken() {
        accessTokenCache.clear();
    }

    /**
     * 列出指定命名空间下的所有服务名。
     */
    public List<String> listServiceNames(String namespace) {
        String accessToken = getAccessToken();
        String nacosAddr = nacosConfig.getAddr();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("accessToken", accessToken);
        params.put("namespaceId", resolveNamespace(namespace));
        params.put("pageNo", "1");
        params.put("pageSize", "1000");

        try {
            String url = buildUrl(nacosAddr, NACOS_SERVICE_LIST_PATH, params);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JSONObject result = JSON.parseObject(response.getBody());
            if (result == null) {
                return Collections.emptyList();
            }
            int count = result.getIntValue("count", 0);
            if (count == 0) {
                return Collections.emptyList();
            }
            JSONArray doms = result.getJSONArray("doms");
            if (doms == null || doms.isEmpty()) {
                return Collections.emptyList();
            }
            return doms.toJavaList(String.class);
        } catch (Exception e) {
            log.error("[NacosHttpClient] listServiceNames error, addr: {}, namespace: {}", nacosAddr, namespace, e);
            return Collections.emptyList();
        }
    }

    /**
     * 查询指定命名空间和服务下的实例列表。
     */
    public List<NacosInstanceInfo> listInstances(String namespace, String serviceName) {
        String accessToken = getAccessToken();
        String nacosAddr = nacosConfig.getAddr();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("accessToken", accessToken);
        params.put("namespaceId", resolveNamespace(namespace));
        params.put("serviceName", serviceName);
        params.put("clusterName", "DEFAULT");

        try {
            String url = buildUrl(nacosAddr, NACOS_SERVICE_INSTANCE_PATH, params);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JSONObject result = JSON.parseObject(response.getBody());
            if (result == null) {
                return Collections.emptyList();
            }
            JSONArray hosts = result.getJSONArray("hosts");
            if (hosts == null || hosts.isEmpty()) {
                return Collections.emptyList();
            }
            List<NacosInstanceInfo> instances = new ArrayList<>();
            for (int i = 0; i < hosts.size(); i++) {
                JSONObject host = hosts.getJSONObject(i);
                instances.add(new NacosInstanceInfo(
                        host.getString("ip"),
                        host.getIntValue("port"),
                        host.getBooleanValue("healthy")
                ));
            }
            return instances;
        } catch (Exception e) {
            log.error("[NacosHttpClient] listInstances error, addr: {}, namespace: {}, service: {}",
                    nacosAddr, namespace, serviceName, e);
            return Collections.emptyList();
        }
    }

    /**
     * 分页查询配置列表。
     */
    public List<NacosConfigInfo> listConfigs(String namespace, int pageNo, int pageSize) {
        String accessToken = getAccessToken();
        String nacosAddr = nacosConfig.getAddr();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("accessToken", accessToken);
        params.put("tenant", resolveNamespace(namespace));
        params.put("dataId", "");
        params.put("search", "blur");
        params.put("group", "");
        params.put("appName", "");
        params.put("config_tags", "");
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));

        try {
            String url = buildUrl(nacosAddr, NACOS_CONFIG_LIST_PATH, params);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JSONObject result = JSON.parseObject(response.getBody());
            if (result == null || result.getIntValue("totalCount", 0) == 0) {
                return Collections.emptyList();
            }
            JSONArray pageItems = result.getJSONArray("pageItems");
            if (pageItems == null || pageItems.isEmpty()) {
                return Collections.emptyList();
            }
            List<NacosConfigInfo> configs = new ArrayList<>();
            for (int i = 0; i < pageItems.size(); i++) {
                JSONObject item = pageItems.getJSONObject(i);
                configs.add(new NacosConfigInfo(
                        item.getString("dataId"),
                        item.getString("group"),
                        item.getString("appName"),
                        item.getString("content"),
                        item.getString("md5"),
                        item.getString("id"),
                        item.getString("type"),
                        item.getLong("lastModified")
                ));
            }
            return configs;
        } catch (Exception e) {
            log.error("[NacosHttpClient] listConfigs error, addr: {}, namespace: {}", nacosAddr, namespace, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取单个配置详情。
     */
    public NacosConfigDetail getConfig(String namespace, String dataId, String group) {
        String accessToken = getAccessToken();
        String nacosAddr = nacosConfig.getAddr();

        Map<String, String> params = new LinkedHashMap<>();
        params.put("accessToken", accessToken);
        params.put("tenant", resolveNamespace(namespace));
        params.put("dataId", dataId);
        params.put("group", group != null ? group : "DEFAULT_GROUP");
        params.put("search", "accurate");
        params.put("pageNo", "1");
        params.put("pageSize", "1");

        try {
            String url = buildUrl(nacosAddr, NACOS_CONFIG_LIST_PATH, params);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JSONObject result = JSON.parseObject(response.getBody());
            if (result != null) {
                JSONArray pageItems = result.getJSONArray("pageItems");
                if (pageItems != null && !pageItems.isEmpty()) {
                    JSONObject config = pageItems.getJSONObject(0);
                    return new NacosConfigDetail(
                            config.getString("content"),
                            config.getString("dataId"),
                            config.getString("group"),
                            config.getString("id"),
                            config.getString("md5"),
                            config.getString("appName"),
                            config.getLong("lastModified")
                    );
                }
            }
            return null;
        } catch (Exception e) {
            log.error("[NacosHttpClient] getConfig error, addr: {}, namespace: {}, dataId: {}, group: {}",
                    nacosAddr, namespace, dataId, group, e);
            return null;
        }
    }

    /**
     * 发布配置到 Nacos。
     */
    public boolean publishConfig(String namespace, String dataId, String group,
                                  String appName, String id, String md5,
                                  String content, String configType) {
        String accessToken = getAccessToken();
        String nacosAddr = nacosConfig.getAddr();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
        formParams.add("accessToken", accessToken);
        formParams.add("tenant", resolveNamespace(namespace));
        formParams.add("dataId", dataId);
        formParams.add("group", group);
        formParams.add("appName", appName != null ? appName : "");
        formParams.add("id", id != null ? id : "");
        formParams.add("md5", md5 != null ? md5 : "");
        formParams.add("content", content);
        formParams.add("configType", configType != null ? configType : "yaml");

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(formParams, headers);

        try {
            String url = nacosAddr + NACOS_CONFIG_LIST_PATH + "?accessToken=" + accessToken;
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            log.info("[NacosHttpClient] publishConfig response: {}", response.getBody());
            return true;
        } catch (Exception e) {
            log.error("[NacosHttpClient] publishConfig error", e);
            return false;
        }
    }

    // ─── 内部辅助 ───

    private String getAccessToken() {
        String nacosAddr = nacosConfig.getAddr();

        if (accessTokenCache.containsKey(nacosAddr)) {
            return accessTokenCache.get(nacosAddr);
        }

        String username = nacosConfig.getUsername();
        String password = nacosConfig.getPassword();

        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
                formParams.add("username", username);
                formParams.add("password", password);

                HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(formParams, headers);
                ResponseEntity<String> response = restTemplate.exchange(
                        nacosAddr + NACOS_LOGIN_PATH, HttpMethod.POST, requestEntity, String.class);
                JSONObject json = JSON.parseObject(response.getBody());
                if (json != null) {
                    String token = json.getString("accessToken");
                    if (token != null) {
                        accessTokenCache.put(nacosAddr, token);
                        return token;
                    }
                }
            } catch (Exception e) {
                log.error("[NacosHttpClient] nacos login error", e);
            }
        }

        return "";
    }

    private String buildUrl(String baseUrl, String path, Map<String, String> params) {
        StringBuilder url = new StringBuilder(baseUrl).append(path).append("?");
        params.forEach((key, value) -> url.append(key).append("=").append(value).append("&"));
        return url.substring(0, url.length() - 1);
    }

    /**
     * Nacos Open API 中，public 命名空间需要传空字符串，非 public 传实际值。
     */
    private String resolveNamespace(String namespace) {
        return Objects.equals(namespace, "public") ? "" : (namespace != null ? namespace : "");
    }

    // ─── 内部 DTO ───

    public static class NacosInstanceInfo {
        private final String ip;
        private final int port;
        private final boolean healthy;

        public NacosInstanceInfo(String ip, int port, boolean healthy) {
            this.ip = ip;
            this.port = port;
            this.healthy = healthy;
        }

        public String getIp() { return ip; }
        public int getPort() { return port; }
        public boolean isHealthy() { return healthy; }
    }

    public static class NacosConfigInfo {
        private final String dataId;
        private final String group;
        private final String appName;
        private final String content;
        private final String md5;
        private final String id;
        private final String type;
        private final Long lastModified;

        public NacosConfigInfo(String dataId, String group, String appName, String content,
                               String md5, String id, String type, Long lastModified) {
            this.dataId = dataId;
            this.group = group;
            this.appName = appName;
            this.content = content;
            this.md5 = md5;
            this.id = id;
            this.type = type;
            this.lastModified = lastModified;
        }

        public String getDataId() { return dataId; }
        public String getGroup() { return group; }
        public String getAppName() { return appName; }
        public String getContent() { return content; }
        public String getMd5() { return md5; }
        public String getId() { return id; }
        public String getType() { return type; }
        public Long getLastModified() { return lastModified; }
    }

    public static class NacosConfigDetail {
        private final String content;
        private final String dataId;
        private final String group;
        private final String id;
        private final String md5;
        private final String appName;
        private final Long modifyTime;

        public NacosConfigDetail(String content, String dataId, String group,
                                  String id, String md5, String appName, Long modifyTime) {
            this.content = content;
            this.dataId = dataId;
            this.group = group;
            this.id = id;
            this.md5 = md5;
            this.appName = appName;
            this.modifyTime = modifyTime;
        }

        public String getContent() { return content; }
        public String getDataId() { return dataId; }
        public String getGroup() { return group; }
        public String getId() { return id; }
        public String getMd5() { return md5; }
        public String getAppName() { return appName; }
        public Long getModifyTime() { return modifyTime; }
    }
}

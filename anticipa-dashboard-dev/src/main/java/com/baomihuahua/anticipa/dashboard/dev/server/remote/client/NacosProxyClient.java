package com.baomihuahua.anticipa.dashboard.dev.server.remote.client;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomihuahua.anticipa.dashboard.dev.server.config.AnticipaProperties;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigListRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosServiceListRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosServiceRespDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NacosProxyClient {

    private final AnticipaProperties anticipaProperties;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String NACOS_LOGIN_PATH = "/nacos/v1/auth/login";
    private static final String NACOS_CONFIG_LIST_PATH = "/nacos/v1/cs/configs";
    private static final String NACOS_CONFIG_HISTORY_PATH = "/nacos/v1/cs/history";
    private static final String NACOS_SERVICE_LIST_PATH = "/nacos/v1/ns/service/list";
    private static final String NACOS_SERVICE_INSTANCE_PATH = "/nacos/v1/ns/instance/list";
    private static final String NACOS_CATALOG_INSTANCES_PATH = "/nacos/v1/ns/catalog/instances";

    private final Map<String, String> accessTokenCache = new ConcurrentHashMap<>();

    public void refreshToken() {
        accessTokenCache.clear();
    }

    /**
     * 分页查询配置列表（只返回 pageItems）
     */
    public List<NacosConfigRespDTO> listConfig(String namespace, int pageNo, int pageSize) {
        String accessToken = getAccessToken();
        String nacosAddr = anticipaProperties.getNacosAddr();

        Map<String, String> params = new HashMap<>();
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
            NacosConfigListRespDTO responseBody = JSON.parseObject(response.getBody(), NacosConfigListRespDTO.class);
            if (responseBody == null || responseBody.getTotalCount() == 0) {
                return Collections.emptyList();
            }

            List<NacosConfigRespDTO> pageItems = responseBody.getPageItems();
            if (pageItems == null || pageItems.isEmpty()) {
                return Collections.emptyList();
            }

            return pageItems;
        } catch (Exception e) {
            log.error("list config error, nacosAddr: {}, namespace: {}", nacosAddr, namespace, e);
            return Collections.emptyList();
        }
    }

    /**
     * 分页查询配置列表（返回包含 totalCount 的完整分页结果）
     */
    public NacosConfigListRespDTO listConfigWithTotal(String namespace, int pageNo, int pageSize) {
        return listConfigWithTotal(namespace, pageNo, pageSize, null);
    }

    /**
     * 分页查询配置列表（返回包含 totalCount 的完整分页结果），支持 appName 过滤
     */
    public NacosConfigListRespDTO listConfigWithTotal(String namespace, int pageNo, int pageSize, String appName) {
        String accessToken = getAccessToken();
        String nacosAddr = anticipaProperties.getNacosAddr();

        Map<String, String> params = new HashMap<>();
        params.put("accessToken", accessToken);
        params.put("tenant", resolveNamespace(namespace));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        params.put("dataId", "");
        params.put("search", "blur");
        params.put("group", "");
        params.put("appName", appName != null ? appName : "");
        params.put("config_tags", "");

        try {
            String url = buildUrl(nacosAddr, NACOS_CONFIG_LIST_PATH, params);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            NacosConfigListRespDTO result = JSON.parseObject(response.getBody(), NacosConfigListRespDTO.class);
            if (result == null) {
                return NacosConfigListRespDTO.builder().totalCount(0).pageItems(Collections.emptyList()).build();
            }
            return result;
        } catch (Exception e) {
            log.error("list config with total error, nacosAddr: {}, namespace: {}", nacosAddr, namespace, e);
            return NacosConfigListRespDTO.builder().totalCount(0).pageItems(Collections.emptyList()).build();
        }
    }

    /**
     * 获取单个配置详情（使用 search=accurate 精确查询）
     */
    public NacosConfigDetailRespDTO getConfig(String namespace, String dataId, String group) {
        String accessToken = getAccessToken();
        String nacosAddr = anticipaProperties.getNacosAddr();

        Map<String, String> params = new HashMap<>();
        params.put("accessToken", accessToken);
        params.put("tenant", resolveNamespace(namespace));
        params.put("dataId", dataId);
        params.put("group", group);
        params.put("search", "accurate");
        params.put("pageNo", "1");
        params.put("pageSize", "1");

        try {
            String url = buildUrl(nacosAddr, NACOS_CONFIG_LIST_PATH, params);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            NacosConfigListRespDTO result = JSON.parseObject(response.getBody(), NacosConfigListRespDTO.class);
            if (result != null && result.getPageItems() != null && !result.getPageItems().isEmpty()) {
                NacosConfigRespDTO config = result.getPageItems().get(0);
                return NacosConfigDetailRespDTO.builder()
                        .content(config.getContent())
                        .dataId(config.getDataId())
                        .group(config.getGroup())
                        .id(config.getId())
                        .md5(config.getMd5())
                        .appName(config.getAppName())
                        .modifyTime(config.getLastModified())
                        .build();
            }
            return null;
        } catch (Exception e) {
            log.error("get config error, nacosAddr: {}, namespace: {}, dataId: {}, group: {}", nacosAddr, namespace, dataId, group, e);
            return null;
        }
    }

    /**
     * 通过历史接口获取配置的最新修改时间
     */
    public Long getConfigModifyTime(String namespace, String dataId, String group) {
        String accessToken = getAccessToken();
        String nacosAddr = anticipaProperties.getNacosAddr();

        Map<String, String> params = new HashMap<>();
        params.put("accessToken", accessToken);
        params.put("tenant", resolveNamespace(namespace));
        params.put("dataId", dataId);
        params.put("group", group);
        params.put("pageNo", "1");
        params.put("pageSize", "1");

        try {
            String url = buildUrl(nacosAddr, NACOS_CONFIG_HISTORY_PATH, params);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            com.alibaba.fastjson2.JSONObject result = JSON.parseObject(response.getBody());
            if (result != null) {
                com.alibaba.fastjson2.JSONArray pageItems = result.getJSONArray("pageItems");
                if (pageItems != null && !pageItems.isEmpty()) {
                    com.alibaba.fastjson2.JSONObject latest = pageItems.getJSONObject(0);
                    Object modifyTime = latest.get("lastModifiedTime");
                    if (modifyTime instanceof Long) {
                        return (Long) modifyTime;
                    } else if (modifyTime != null) {
                        // 兼容日期字符串格式
                        try {
                            return Long.parseLong(modifyTime.toString());
                        } catch (NumberFormatException ex) {
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
                            return sdf.parse(modifyTime.toString()).getTime();
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("get config modify time error, nacosAddr: {}, namespace: {}, dataId: {}, group: {}", nacosAddr, namespace, dataId, group, e);
            return null;
        }
    }

    /**
     * 列出指定命名空间下的所有服务名。
     * <p>
     * 使用 Nacos Open API: /nacos/v1/ns/service/list
     * 返回格式: {"count": N, "doms": ["service1", "service2", ...]}
     * </p>
     */
    public List<String> listServiceNames(String namespace) {
        String accessToken = getAccessToken();
        String nacosAddr = anticipaProperties.getNacosAddr();

        Map<String, String> params = new HashMap<>();
        params.put("accessToken", accessToken);
        params.put("namespaceId", resolveNamespace(namespace));
        params.put("pageNo", "1");
        params.put("pageSize", "1000");

        try {
            String url = buildUrl(nacosAddr, NACOS_SERVICE_LIST_PATH, params);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            com.alibaba.fastjson2.JSONObject result = JSON.parseObject(response.getBody());
            if (result == null) {
                return Collections.emptyList();
            }
            int count = result.getIntValue("count", 0);
            if (count == 0) {
                return Collections.emptyList();
            }
            com.alibaba.fastjson2.JSONArray doms = result.getJSONArray("doms");
            if (doms == null || doms.isEmpty()) {
                return Collections.emptyList();
            }
            return doms.toJavaList(String.class);
        } catch (Exception e) {
            log.error("list service names error, nacosAddr: {}, namespace: {}", nacosAddr, namespace, e);
            return Collections.emptyList();
        }
    }

    /**
     * 查询指定服务的实例列表。
     * <p>
     * 使用 Nacos Open API: /nacos/v1/ns/instance/list
     * 返回格式: {"name": "xxx", "hosts": [{"instanceId": "...", "ip": "...", "port": N, "healthy": true, ...}]}
     * </p>
     */
    public List<NacosServiceRespDTO> listInstances(String namespace, String serviceName) {
        String accessToken = getAccessToken();
        String nacosAddr = anticipaProperties.getNacosAddr();

        Map<String, String> params = new HashMap<>();
        params.put("accessToken", accessToken);
        params.put("namespaceId", resolveNamespace(namespace));
        params.put("serviceName", serviceName);
        params.put("clusterName", "DEFAULT");

        try {
            String url = buildUrl(nacosAddr, NACOS_SERVICE_INSTANCE_PATH, params);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            com.alibaba.fastjson2.JSONObject result = JSON.parseObject(response.getBody());
            if (result == null) {
                return Collections.emptyList();
            }
            com.alibaba.fastjson2.JSONArray hosts = result.getJSONArray("hosts");
            if (hosts == null || hosts.isEmpty()) {
                return Collections.emptyList();
            }
            return hosts.stream().map(obj -> {
                com.alibaba.fastjson2.JSONObject host = (com.alibaba.fastjson2.JSONObject) obj;
                return NacosServiceRespDTO.builder()
                        .ip(host.getString("ip"))
                        .port(host.getIntValue("port"))
                        .serviceName(serviceName)
                        .clusterName(host.getString("clusterName"))
                        .build();
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("list instances error, nacosAddr: {}, namespace: {}, serviceName: {}", nacosAddr, namespace, serviceName, e);
            return Collections.emptyList();
        }
    }

    public NacosServiceListRespDTO getService(String namespace, String serviceName) {
        String accessToken = getAccessToken();
        String nacosAddr = anticipaProperties.getNacosAddr();

        Map<String, String> params = new HashMap<>();
        params.put("accessToken", accessToken);
        params.put("namespaceId", resolveNamespace(namespace));
        params.put("serviceName", serviceName == null ? "" : serviceName);
        params.put("clusterName", "DEFAULT");
        params.put("groupName", "DEFAULT_GROUP");
        params.put("pageNo", "1");
        params.put("pageSize", "100");

        try {
            String url = buildUrl(nacosAddr, NACOS_CATALOG_INSTANCES_PATH, params);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return JSON.parseObject(response.getBody(), NacosServiceListRespDTO.class);
        } catch (Exception e) {
            log.error("get service error, nacosAddr: {}, namespace: {}, serviceName: {}", nacosAddr, namespace, serviceName, e);
            return null;
        }
    }

    public void publishConfig(String namespace, String dataId, String group, String appName, String id, String md5, String content, String configType) {
        String accessToken = getAccessToken();
        String nacosAddr = anticipaProperties.getNacosAddr();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("accessToken", accessToken);
        params.add("tenant", resolveNamespace(namespace));
        params.add("dataId", dataId);
        params.add("group", group);
        params.add("appName", appName);
        params.add("id", id);
        params.add("md5", md5);
        params.add("content", content);
        params.add("configType", configType);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);

        try {
            String url = nacosAddr + NACOS_CONFIG_LIST_PATH + "?accessToken=" + accessToken;
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            log.info("publish config response: {}", response.getBody());
        } catch (Exception e) {
            log.error("publish config error", e);
        }
    }

    private String getAccessToken() {
        String nacosAddr = anticipaProperties.getNacosAddr();

        if (accessTokenCache.containsKey(nacosAddr)) {
            return accessTokenCache.get(nacosAddr);
        }

        String nacosUsername = anticipaProperties.getNacosUsername();
        String nacosPassword = anticipaProperties.getNacosPassword();

        if (StrUtil.isAllNotBlank(nacosUsername, nacosPassword)) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
                params.add("username", nacosUsername);
                params.add("password", nacosPassword);

                HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);
                ResponseEntity<String> response = restTemplate.exchange(nacosAddr + NACOS_LOGIN_PATH, HttpMethod.POST, requestEntity, String.class);
                String accessToken = JSON.parseObject(response.getBody()).getString("accessToken");
                accessTokenCache.put(nacosAddr, accessToken);
                return accessToken;
            } catch (Exception e) {
                log.error("nacos login error", e);
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
     * Nacos Open API 中，public 命名空间需要传空字符串，非 public 传实际值
     */
    private String resolveNamespace(String namespace) {
        return Objects.equals(namespace, "public") ? "" : namespace;
    }
}

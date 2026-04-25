package com.baomihuahua.anticipa.dashboard.dev.server.remote.client;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
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

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NacosProxyClient {

    private final AnticipaProperties anticipaProperties;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String NACOS_LOGIN_PATH = "/nacos/v1/auth/login";
    private static final String NACOS_CONFIG_LIST_PATH = "/nacos/v1/cs/configs";
    private static final String NACOS_CONFIG_DETAIL_PATH = "/nacos/v1/cs/config";
    private static final String NACOS_SERVICE_LIST_PATH = "/nacos/v1/ns/service/list";
    private static final String NACOS_SERVICE_INSTANCE_PATH = "/nacos/v1/ns/instance/list";

    private final Map<String, String> accessTokenCache = new ConcurrentHashMap<>();

    public void refreshToken() {
        accessTokenCache.clear();
    }

    public List<NacosConfigRespDTO> listConfig(String namespace) {
        return listConfig(namespace, 1, 1000);
    }

    public List<NacosConfigRespDTO> listConfig(String namespace, int pageNo, int pageSize) {
        String accessToken = getAccessToken();
        String nacosAddr = anticipaProperties.getNacosAddr();

        Map<String, String> params = new HashMap<>();
        params.put("accessToken", accessToken);
        params.put("tenant", namespace);
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

    public NacosConfigListRespDTO listConfigWithTotal(String namespace, int pageNo, int pageSize) {
        String accessToken = getAccessToken();
        String nacosAddr = anticipaProperties.getNacosAddr();

        Map<String, String> params = new HashMap<>();
        params.put("accessToken", accessToken);
        params.put("tenant", namespace);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));

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

    public NacosConfigDetailRespDTO getConfig(String namespace, String dataId, String group) {
        String accessToken = getAccessToken();
        String nacosAddr = anticipaProperties.getNacosAddr();

        Map<String, String> params = new HashMap<>();
        params.put("accessToken", accessToken);
        params.put("tenant", namespace);
        params.put("dataId", dataId);
        params.put("group", group);

        try {
            String url = buildUrl(nacosAddr, NACOS_CONFIG_DETAIL_PATH, params);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return JSON.parseObject(response.getBody(), NacosConfigDetailRespDTO.class);
        } catch (Exception e) {
            log.error("get config error, nacosAddr: {}, namespace: {}, dataId: {}, group: {}", nacosAddr, namespace, dataId, group, e);
            return null;
        }
    }

    public NacosServiceListRespDTO getService(String namespace, String serviceName) {
        String accessToken = getAccessToken();
        String nacosAddr = anticipaProperties.getNacosAddr();

        Map<String, String> params = new HashMap<>();
        params.put("accessToken", accessToken);
        params.put("tenant", namespace);
        params.put("serviceName", serviceName);

        try {
            String url = buildUrl(nacosAddr, NACOS_SERVICE_INSTANCE_PATH, params);
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
        params.add("tenant", namespace);
        params.add("dataId", dataId);
        params.add("group", group);
        params.add("appName", appName);
        params.add("id", id);
        params.add("md5", md5);
        params.add("content", content);
        params.add("configType", configType);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);

        try {
            String url = nacosAddr + NACOS_CONFIG_DETAIL_PATH + "?accessToken=" + accessToken;
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
}

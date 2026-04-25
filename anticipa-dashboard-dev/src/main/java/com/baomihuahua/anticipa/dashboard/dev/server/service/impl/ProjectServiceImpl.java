package com.baomihuahua.anticipa.dashboard.dev.server.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.config.AnticipaProperties;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ProjectInfoRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.client.NacosProxyClient;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosConfigRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.remote.dto.NacosServiceListRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final AnticipaProperties anticipaProperties;
    private final NacosProxyClient nacosProxyClient;

    @Override
    public List<ProjectInfoRespDTO> listProject() {
        return buildProjectList();
    }

    @Override
    public PageDTO<ProjectInfoRespDTO> listProjectPage(PageReqDTO pageReq) {
        List<ProjectInfoRespDTO> allProjects = buildProjectList();
        int offset = pageReq.getOffset();
        int size = pageReq.getSize();
        int total = allProjects.size();

        if (offset >= total) {
            return PageDTO.of(new ArrayList<>(), total);
        }

        int toIndex = Math.min(offset + size, total);
        List<ProjectInfoRespDTO> pageRecords = allProjects.subList(offset, toIndex);
        return PageDTO.of(pageRecords, total);
    }

    private List<ProjectInfoRespDTO> buildProjectList() {
        // 用 LinkedHashMap 按 namespace+appName 去重，保持顺序
        Map<String, ProjectInfoRespDTO> projectMap = new LinkedHashMap<>();

        List<String> namespaces = anticipaProperties.getNamespaces();
        namespaces.forEach(namespace -> {
            List<NacosConfigRespDTO> configs = nacosProxyClient.listConfig(namespace);
            if (CollUtil.isEmpty(configs)) {
                return;
            }

            configs.stream()
                    .filter(each -> each.getAppName() != null)
                    .forEach(config -> {
                        String key = namespace + ":" + config.getAppName();
                        if (!projectMap.containsKey(key)) {
                            NacosServiceListRespDTO service = nacosProxyClient.getService(namespace, config.getAppName());
                            ProjectInfoRespDTO projectInfo = ProjectInfoRespDTO.builder()
                                    .namespace(namespace)
                                    .serviceName(config.getAppName())
                                    .instanceCount(service != null ? service.getCount() : 0)
                                    .build();
                            projectMap.put(key, projectInfo);
                        }
                    });
        });

        return new ArrayList<>(projectMap.values());
    }
}

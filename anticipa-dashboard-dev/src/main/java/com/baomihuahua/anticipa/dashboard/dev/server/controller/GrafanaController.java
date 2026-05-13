package com.baomihuahua.anticipa.dashboard.dev.server.controller;

import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.baomihuahua.anticipa.dashboard.dev.server.config.AnticipaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/anticipa-dashboard/grafana")
@RequiredArgsConstructor
public class GrafanaController {

    private final AnticipaProperties anticipaProperties;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        String url = anticipaProperties.getGrafana().getUrl();
        boolean configured = url != null && !url.isEmpty();
        return Result.success(Map.of(
                "configured", configured,
                "url", configured ? url : ""
        ));
    }
}

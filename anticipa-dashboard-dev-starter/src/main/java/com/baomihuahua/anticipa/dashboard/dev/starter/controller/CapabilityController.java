package com.baomihuahua.anticipa.dashboard.dev.starter.controller;

import com.baomihuahua.anticipa.dashboard.dev.starter.core.Result;
import com.baomihuahua.anticipa.dashboard.dev.starter.core.Results;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 能力探测接口，供前端判断当前后端加载了哪些模块
 */
@RestController
@RequestMapping("/api/anticipa-dashboard")
public class CapabilityController {

    private final boolean agentAvailable;

    public CapabilityController() {
        this.agentAvailable = isClassPresent("com.baomihuahua.anticipa.agent.AgentLoop");
    }

    @GetMapping("/capabilities")
    public Result<CapabilityInfo> capabilities() {
        CapabilityInfo info = new CapabilityInfo();
        info.setDynamicThreadPool(true);
        info.setWebThreadPool(true);
        info.setAiAgent(agentAvailable);
        return Results.success(info);
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Data
    public static class CapabilityInfo {
        private boolean dynamicThreadPool;
        private boolean webThreadPool;
        private boolean aiAgent;
    }
}

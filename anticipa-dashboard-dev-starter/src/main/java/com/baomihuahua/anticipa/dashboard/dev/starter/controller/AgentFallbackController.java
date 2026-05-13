package com.baomihuahua.anticipa.dashboard.dev.starter.controller;

import com.baomihuahua.anticipa.dashboard.dev.starter.core.Result;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 模块未引入时的降级 Controller
 * 当 classpath 上不存在 AgentLoop 时生效，对所有 /agent/** 请求返回明确的提示信息
 */
@RestController
@RequestMapping("/api/anticipa-dashboard")
public class AgentFallbackController {

    private static final String AGENT_NOT_AVAILABLE_CODE = "AGENT_NOT_AVAILABLE";
    private static final String AGENT_NOT_AVAILABLE_MESSAGE =
            "AI 智能调优功能未启用。请在项目中引入 anticipa-agent 依赖并配置 anticipa.agent.enabled=true 后重启应用。";

    @RequestMapping("/agent/**")
    public Result<Void> fallback() {
        return new Result<Void>()
                .setCode(AGENT_NOT_AVAILABLE_CODE)
                .setMessage(AGENT_NOT_AVAILABLE_MESSAGE);
    }
}

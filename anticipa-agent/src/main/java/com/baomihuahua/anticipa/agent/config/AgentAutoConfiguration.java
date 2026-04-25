package com.baomihuahua.anticipa.agent.config;

import com.baomihuahua.anticipa.agent.AgentLoop;
import com.baomihuahua.anticipa.agent.ToolRegistry;
import com.baomihuahua.anticipa.agent.llm.AIClient;
import com.baomihuahua.anticipa.agent.llm.OpenAIClient;
import com.baomihuahua.anticipa.agent.safety.SafetyGuard;
import com.baomihuahua.anticipa.agent.approval.ApprovalService;
import com.baomihuahua.anticipa.agent.audit.AuditStore;
import com.baomihuahua.anticipa.agent.session.SessionManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
@ConditionalOnProperty(prefix = "anticipa.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AIClient aiClient(AgentProperties properties) {
        return new OpenAIClient(properties.getApiKey(), properties.getBaseUrl(), properties.getModel());
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public SafetyGuard safetyGuard() {
        return new SafetyGuard();
    }

    @Bean
    @ConditionalOnMissingBean
    public ApprovalService approvalService() {
        return new ApprovalService();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditStore auditStore() {
        return new AuditStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager() {
        return new SessionManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentLoop agentLoop(AIClient aiClient, ToolRegistry toolRegistry,
                               SafetyGuard safetyGuard, ApprovalService approvalService,
                               AuditStore auditStore) {
        return new AgentLoop(aiClient, toolRegistry, safetyGuard, approvalService, auditStore);
    }
}

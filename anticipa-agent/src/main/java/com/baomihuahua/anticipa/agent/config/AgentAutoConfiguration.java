package com.baomihuahua.anticipa.agent.config;

import com.baomihuahua.anticipa.agent.*;
import com.baomihuahua.anticipa.agent.context.ContextManager;
import com.baomihuahua.anticipa.agent.event.ApprovalFutureManager;
import com.baomihuahua.anticipa.agent.event.RejectionAnalysisListener;
import com.baomihuahua.anticipa.agent.knowledge.FileKnowledgeRetriever;
import com.baomihuahua.anticipa.agent.knowledge.KnowledgeService;
import com.baomihuahua.anticipa.agent.llm.AIClient;
import com.baomihuahua.anticipa.agent.llm.OpenAIClient;
import com.baomihuahua.anticipa.agent.memory.FileMemoryStore;
import com.baomihuahua.anticipa.agent.memory.MemoryManager;
import com.baomihuahua.anticipa.agent.memory.MemoryStore;
import com.baomihuahua.anticipa.agent.permission.PermissionManager;
import com.baomihuahua.anticipa.agent.prompt.SystemPromptBuilder;
import com.baomihuahua.anticipa.agent.safety.SafetyGuard;
import com.baomihuahua.anticipa.agent.approval.ApprovalService;
import com.baomihuahua.anticipa.agent.audit.AuditStore;
import com.baomihuahua.anticipa.agent.session.SessionManager;
import com.baomihuahua.anticipa.agent.scheduled.*;
import com.baomihuahua.anticipa.agent.discovery.*;
import com.baomihuahua.anticipa.agent.discovery.remote.NacosHttpClient;
import com.baomihuahua.anticipa.agent.discovery.remote.RemoteInstanceDiscoveryService;
import com.baomihuahua.anticipa.agent.discovery.remote.RemoteThreadPoolQueryService;
import com.baomihuahua.anticipa.agent.tool.AdjustThreadPoolTool;
import com.baomihuahua.anticipa.agent.tool.ListInstanceThreadPoolsTool;
import com.baomihuahua.anticipa.agent.tool.ListNamespacesTool;
import com.baomihuahua.anticipa.agent.tool.ListNacosConfigsTool;
import com.baomihuahua.anticipa.agent.tool.ListServicesTool;
import com.baomihuahua.anticipa.agent.tool.ListThreadPoolsTool;
import com.baomihuahua.anticipa.agent.tool.QueryThreadPoolTool;
import com.baomihuahua.anticipa.agent.tool.RemoteAnalyzeTrendsTool;
import com.baomihuahua.anticipa.agent.tool.RemoteCheckLogsExistTool;
import com.baomihuahua.anticipa.agent.tool.RemoteQueryHistoryTool;
import com.baomihuahua.anticipa.agent.tool.ToolSearchService;
import com.baomihuahua.anticipa.agent.tool.UpdateThreadPoolConfigTool;
import com.baomihuahua.anticipa.core.config.ThreadPoolLogConfig;
import com.baomihuahua.anticipa.core.monitor.ThreadPoolLogStore;
import com.baomihuahua.anticipa.core.notification.service.NotifierDispatcher;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 使用 {@code AutoConfigureAfter(name=…)} 与 spring-base 的装配顺序对齐，无需依赖该模块。 */
@Configuration
@AutoConfigureAfter(name = "com.baomihuahua.anticipa.spring.base.configuration.AnticipaBaseConfiguration")
@EnableConfigurationProperties(AgentProperties.class)
@ConditionalOnProperty(prefix = "anticipa.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentAutoConfiguration {

    // ─── LLM ───

    @Bean
    @ConditionalOnMissingBean
    public AIClient aiClient(AgentProperties properties) {
        return new OpenAIClient(properties.getApiKey(), properties.getBaseUrl(), properties.getModel());
    }

    // ─── 工具系统 ───

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolSearchService toolSearchService(ToolRegistry toolRegistry) {
        return new ToolSearchService(toolRegistry, 8, true);
    }

    // ─── 权限系统（Deny-First，替代 SafetyGuard） ───

    @Bean
    @ConditionalOnMissingBean
    public PermissionManager permissionManager(ToolRegistry toolRegistry) {
        return new PermissionManager(toolRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public SafetyGuard safetyGuard() {
        return new SafetyGuard();
    }

    // ─── 审批、审计、会话 ───

    @Bean
    @ConditionalOnMissingBean
    public ApprovalFutureManager approvalFutureManager() {
        return new ApprovalFutureManager();
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
    public SessionManager sessionManager(AgentProperties properties) {
        return new SessionManager(properties);
    }

    // ─── 记忆管理 ───

    @Bean
    @ConditionalOnMissingBean
    public MemoryStore memoryStore(AgentProperties properties) {
        return new FileMemoryStore(properties.getMemoryPath());
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryManager memoryManager(MemoryStore memoryStore) {
        return new MemoryManager(memoryStore);
    }

    // ─── Agent 核心引擎 ───

    @Bean
    @ConditionalOnMissingBean
    public AgentLoop agentLoop(AIClient aiClient, ToolRegistry toolRegistry,
                                ToolSearchService toolSearchService,
                                PermissionManager permissionManager,
                                ApprovalService approvalService, AuditStore auditStore,
                                ContextManager contextManager, MemoryManager memoryManager,
                                KnowledgeService knowledgeService,
                                SystemPromptBuilder promptBuilder,
                                ApprovalFutureManager approvalFutureManager) {
        return new AgentLoop(aiClient, toolRegistry, toolSearchService,
                permissionManager, approvalService, auditStore,
                contextManager, memoryManager, knowledgeService, promptBuilder,
                approvalFutureManager);
    }

    // ─── 上下文管理 ───

    @Bean
    @ConditionalOnMissingBean
    public ContextManager contextManager() {
        return new ContextManager(128000, 13000, 2000);
    }

    // ─── 知识库 ───

    @Bean
    @ConditionalOnMissingBean
    public FileKnowledgeRetriever fileKnowledgeRetriever(AgentProperties properties) {
        return new FileKnowledgeRetriever(
                properties.getKnowledgePath(),
                properties.getMemoryPath());
    }

    @Bean(initMethod = "initBuiltinKnowledge")
    @ConditionalOnMissingBean
    public KnowledgeService knowledgeService(FileKnowledgeRetriever retriever, AgentProperties properties) {
        return new KnowledgeService(retriever, properties.getKnowledgePath());
    }

    // ─── Prompt 工程 ───

    @Bean
    @ConditionalOnMissingBean
    public SystemPromptBuilder systemPromptBuilder(ToolRegistry toolRegistry) {
        return new SystemPromptBuilder(toolRegistry);
    }

    // ─── 实例发现与查询服务 ───

    // NacosHttpClient
    @Bean
    @ConditionalOnProperty(prefix = "anticipa.agent.nacos", name = "enabled", havingValue = "true")
    public NacosHttpClient nacosHttpClient(AgentProperties properties) {
        return new NacosHttpClient(properties.getNacos());
    }

    // InstanceDiscoveryService — 远程模式（覆盖本地实现）
    @Bean("instanceDiscoveryService")
    @ConditionalOnProperty(prefix = "anticipa.agent.nacos", name = "enabled", havingValue = "true")
    public InstanceDiscoveryService remoteInstanceDiscoveryService(NacosHttpClient nacosHttpClient,
                                                                    AgentProperties properties) {
        return new RemoteInstanceDiscoveryService(nacosHttpClient, properties.getNacos());
    }

    // ThreadPoolQueryService — 远程模式（覆盖本地实现）
    @Bean("threadPoolQueryService")
    @ConditionalOnProperty(prefix = "anticipa.agent.nacos", name = "enabled", havingValue = "true")
    public ThreadPoolQueryService remoteThreadPoolQueryService(NacosHttpClient nacosHttpClient,
                                                                InstanceDiscoveryService instanceDiscoveryService) {
        return new RemoteThreadPoolQueryService(nacosHttpClient, instanceDiscoveryService);
    }

    // ─── 查询工具（本地/远程均注册） ───

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(InstanceDiscoveryService.class)
    public ListInstancesTool listInstancesTool(ToolRegistry toolRegistry,
                                                InstanceDiscoveryService discoveryService) {
        return new ListInstancesTool(toolRegistry, discoveryService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ThreadPoolQueryService.class)
    public QueryThreadPoolTool queryThreadPoolTool(ToolRegistry toolRegistry,
                                                    ThreadPoolQueryService queryService) {
        return new QueryThreadPoolTool(toolRegistry, queryService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ThreadPoolQueryService.class)
    public UpdateThreadPoolConfigTool updateThreadPoolConfigTool(ToolRegistry toolRegistry,
                                                                  ThreadPoolQueryService queryService) {
        return new UpdateThreadPoolConfigTool(toolRegistry, queryService);
    }

    // ─── 调整工具 + 发现工具（仅远程模式） ───

    @Bean
    @ConditionalOnProperty(prefix = "anticipa.agent.nacos", name = "enabled", havingValue = "true")
    public AdjustThreadPoolTool adjustThreadPoolTool(ToolRegistry toolRegistry,
                                                      ThreadPoolQueryService queryService) {
        return new AdjustThreadPoolTool(toolRegistry, queryService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "anticipa.agent.nacos", name = "enabled", havingValue = "true")
    public ListNamespacesTool listNamespacesTool(ToolRegistry toolRegistry,
                                                   InstanceDiscoveryService discoveryService) {
        return new ListNamespacesTool(toolRegistry, discoveryService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "anticipa.agent.nacos", name = "enabled", havingValue = "true")
    public ListServicesTool listServicesTool(ToolRegistry toolRegistry,
                                               InstanceDiscoveryService discoveryService) {
        return new ListServicesTool(toolRegistry, discoveryService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "anticipa.agent.nacos", name = "enabled", havingValue = "true")
    public ListNacosConfigsTool listNacosConfigsTool(ToolRegistry toolRegistry,
                                                        ThreadPoolQueryService queryService) {
        return new ListNacosConfigsTool(toolRegistry, queryService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "anticipa.agent.nacos", name = "enabled", havingValue = "true")
    public ListInstanceThreadPoolsTool listInstanceThreadPoolsTool(ToolRegistry toolRegistry,
                                                                     ThreadPoolQueryService queryService) {
        return new ListInstanceThreadPoolsTool(toolRegistry, queryService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "anticipa.agent.nacos", name = "enabled", havingValue = "true")
    public ListThreadPoolsTool listThreadPoolsTool(ToolRegistry toolRegistry,
                                                    ThreadPoolQueryService queryService) {
        return new ListThreadPoolsTool(toolRegistry, queryService);
    }

    // ─── 远程诊断工具（HTTP 调用目标实例日志端点；需 Nacos） ───

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "anticipa.agent.nacos", name = "enabled", havingValue = "true")
    public RemoteCheckLogsExistTool remoteCheckLogsExistTool(ToolRegistry toolRegistry,
                                                               ThreadPoolQueryService queryService) {
        return new RemoteCheckLogsExistTool(toolRegistry, queryService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "anticipa.agent.nacos", name = "enabled", havingValue = "true")
    public RemoteQueryHistoryTool remoteQueryHistoryTool(ToolRegistry toolRegistry,
                                                           ThreadPoolQueryService queryService,
                                                           AgentProperties agentProperties) {
        return new RemoteQueryHistoryTool(toolRegistry, queryService, agentProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "anticipa.agent.nacos", name = "enabled", havingValue = "true")
    public RemoteAnalyzeTrendsTool remoteAnalyzeTrendsTool(ToolRegistry toolRegistry,
                                                             ThreadPoolQueryService queryService) {
        return new RemoteAnalyzeTrendsTool(toolRegistry, queryService);
    }

    // ─── 定时任务相关 ───

    @Bean
    @ConditionalOnMissingBean
    public ScheduledTaskService scheduledTaskService(AgentProperties properties) {
        return new ScheduledTaskService(properties.getStorePath(), properties.getTaskLogRetentionDays());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({ThreadPoolQueryService.class, NotifierDispatcher.class})
    public TaskExecutionService taskExecutionService(AgentLoop agentLoop,
                                                      ThreadPoolQueryService queryService,
                                                      ScheduledTaskService taskService,
                                                      NotifierDispatcher notifierDispatcher,
                                                      AgentProperties agentProperties) {
        return new TaskExecutionService(agentLoop, queryService, taskService, notifierDispatcher, agentProperties);
    }

    @Bean(initMethod = "init", destroyMethod = "destroy")
    @ConditionalOnMissingBean
    @ConditionalOnBean(TaskExecutionService.class)
    public TaskScheduler taskScheduler(TaskExecutionService executionService,
                                        ScheduledTaskService taskService) {
        return new TaskScheduler(executionService, taskService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(TaskScheduler.class)
    public CreateScheduledTaskTool createScheduledTaskTool(ToolRegistry toolRegistry,
                                                            ScheduledTaskService taskService,
                                                            TaskScheduler taskScheduler) {
        return new CreateScheduledTaskTool(toolRegistry, taskService, taskScheduler);
    }

    @Bean
    @ConditionalOnMissingBean
    public ListScheduledTasksTool listScheduledTasksTool(ToolRegistry toolRegistry,
                                                          ScheduledTaskService taskService) {
        return new ListScheduledTasksTool(toolRegistry, taskService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(TaskScheduler.class)
    public DeleteScheduledTaskTool deleteScheduledTaskTool(ToolRegistry toolRegistry,
                                                            ScheduledTaskService taskService,
                                                            TaskScheduler taskScheduler) {
        return new DeleteScheduledTaskTool(toolRegistry, taskService, taskScheduler);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(TaskScheduler.class)
    public PauseScheduledTaskTool pauseScheduledTaskTool(ToolRegistry toolRegistry,
                                                          TaskScheduler taskScheduler,
                                                          ScheduledTaskService taskService) {
        return new PauseScheduledTaskTool(toolRegistry, taskScheduler, taskService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(TaskScheduler.class)
    public ResumeScheduledTaskTool resumeScheduledTaskTool(ToolRegistry toolRegistry,
                                                            TaskScheduler taskScheduler,
                                                            ScheduledTaskService taskService) {
        return new ResumeScheduledTaskTool(toolRegistry, taskScheduler, taskService);
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryTaskLogsTool queryTaskLogsTool(ToolRegistry toolRegistry,
                                                ScheduledTaskService taskService) {
        return new QueryTaskLogsTool(toolRegistry, taskService);
    }

    // ─── 拒绝分析事件监听器（依赖 ThreadPoolLogStore + NotifierDispatcher，仅客户端可用） ───

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({ThreadPoolLogStore.class, NotifierDispatcher.class})
    public RejectionAnalysisListener rejectionAnalysisListener(AgentLoop agentLoop,
                                                                ThreadPoolLogStore logStore,
                                                                ThreadPoolLogConfig logConfig,
                                                                NotifierDispatcher notifierDispatcher) {
        return new RejectionAnalysisListener(agentLoop, logStore, logConfig, notifierDispatcher);
    }
}

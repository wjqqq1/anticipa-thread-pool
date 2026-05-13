package com.baomihuahua.anticipa.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "anticipa.agent")
public class AgentProperties {
    private boolean enabled = true;
    private int maxIterations = 15; // 最大循环次数默认15
    private String model;
    private String apiKey;
    private String baseUrl;
    private String apiToken;

    /** 定时任务和数据存储的根路径 */
    private String storePath = "data/agent";

    /** 定时任务执行日志保留天数 */
    private int taskLogRetentionDays = 30;

    /** 知识库路径 */
    private String knowledgePath = "data/knowledge";

    /** 长期记忆路径 */
    private String memoryPath = "data/memory";

    // ─── context 配置 ───
    private int maxTokenBudget = 128000;
    private int compactThreshold = 13000;
    private int toolResultMaxTokens = 2000;

    // ─── 知识库检索配置 ───
    private int knowledgeTopK = 3;
    private boolean autoInitBuiltinKnowledge = true;

    // ─── Tool Search 配置 ───
    private boolean toolSearchEnabled = true;
    private int maxToolsPerRequest = 8;
    private boolean toolSearchFallbackToAll = true;

    /**
     * 静默定时任务拉取历史运行日志时，在时间窗内「最多」取最新多少条（0 表示不限制，易触发大文件全量读，不推荐）。
     */
    private int silentTaskHistoryLimit = 50;

    /**
     * query_history 工具中 limit 下限（含）；与 history-query-limit-max 共同构成允许条数闭区间，超出则工具拒绝执行并提示用户。
     */
    private int historyQueryLimitMin = 50;

    /**
     * query_history 工具中 limit 上限（含）；与 history-query-limit-min 共同构成允许条数闭区间，超出则工具拒绝执行并提示用户。
     */
    private int historyQueryLimitMax = 100;

    // ─── 重试配置 ───
    private int maxRetries = 3;
    private long initialRetryDelayMs = 1000;
    private double retryBackoffMultiplier = 2.0;

    // ─── 权限配置（静态规则路径） ───
    private String permissionsPath;

    // ─── Nacos 远程模式配置 ───
    private NacosConfig nacos = new NacosConfig();

    public NacosConfig getNacos() { return nacos; }
    public void setNacos(NacosConfig nacos) { this.nacos = nacos; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }
    public String getStorePath() { return storePath; }
    public void setStorePath(String storePath) { this.storePath = storePath; }
    public int getTaskLogRetentionDays() { return taskLogRetentionDays; }
    public void setTaskLogRetentionDays(int taskLogRetentionDays) { this.taskLogRetentionDays = taskLogRetentionDays; }
    public String getKnowledgePath() { return knowledgePath; }
    public void setKnowledgePath(String knowledgePath) { this.knowledgePath = knowledgePath; }
    public String getMemoryPath() { return memoryPath; }
    public void setMemoryPath(String memoryPath) { this.memoryPath = memoryPath; }
    public int getMaxTokenBudget() { return maxTokenBudget; }
    public void setMaxTokenBudget(int maxTokenBudget) { this.maxTokenBudget = maxTokenBudget; }
    public int getCompactThreshold() { return compactThreshold; }
    public void setCompactThreshold(int compactThreshold) { this.compactThreshold = compactThreshold; }
    public int getToolResultMaxTokens() { return toolResultMaxTokens; }
    public void setToolResultMaxTokens(int toolResultMaxTokens) { this.toolResultMaxTokens = toolResultMaxTokens; }
    public int getKnowledgeTopK() { return knowledgeTopK; }
    public void setKnowledgeTopK(int knowledgeTopK) { this.knowledgeTopK = knowledgeTopK; }
    public boolean isAutoInitBuiltinKnowledge() { return autoInitBuiltinKnowledge; }
    public void setAutoInitBuiltinKnowledge(boolean autoInitBuiltinKnowledge) { this.autoInitBuiltinKnowledge = autoInitBuiltinKnowledge; }
    public boolean isToolSearchEnabled() { return toolSearchEnabled; }
    public void setToolSearchEnabled(boolean toolSearchEnabled) { this.toolSearchEnabled = toolSearchEnabled; }
    public int getMaxToolsPerRequest() { return maxToolsPerRequest; }
    public void setMaxToolsPerRequest(int maxToolsPerRequest) { this.maxToolsPerRequest = maxToolsPerRequest; }
    public boolean isToolSearchFallbackToAll() { return toolSearchFallbackToAll; }
    public void setToolSearchFallbackToAll(boolean toolSearchFallbackToAll) { this.toolSearchFallbackToAll = toolSearchFallbackToAll; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public long getInitialRetryDelayMs() { return initialRetryDelayMs; }
    public void setInitialRetryDelayMs(long initialRetryDelayMs) { this.initialRetryDelayMs = initialRetryDelayMs; }
    public double getRetryBackoffMultiplier() { return retryBackoffMultiplier; }
    public void setRetryBackoffMultiplier(double retryBackoffMultiplier) { this.retryBackoffMultiplier = retryBackoffMultiplier; }
    public int getSilentTaskHistoryLimit() { return silentTaskHistoryLimit; }
    public void setSilentTaskHistoryLimit(int silentTaskHistoryLimit) { this.silentTaskHistoryLimit = silentTaskHistoryLimit; }
    public int getHistoryQueryLimitMin() { return historyQueryLimitMin; }
    public void setHistoryQueryLimitMin(int historyQueryLimitMin) { this.historyQueryLimitMin = historyQueryLimitMin; }
    public int getHistoryQueryLimitMax() { return historyQueryLimitMax; }
    public void setHistoryQueryLimitMax(int historyQueryLimitMax) { this.historyQueryLimitMax = historyQueryLimitMax; }
    public String getPermissionsPath() { return permissionsPath; }
    public void setPermissionsPath(String permissionsPath) { this.permissionsPath = permissionsPath; }

    /**
     * Nacos 远程模式配置。
     * <p>
     * 启用后，Agent 将通过 Nacos Open API 发现远程实例，
     * 并通过 HTTP 调用客户端端点获取线程池数据。
     * </p>
     */
    public static class NacosConfig {
        /** 是否启用 Nacos 远程模式，默认 false（本地模式） */
        private boolean enabled = false;
        /** Nacos 服务地址，如 http://127.0.0.1:8848 */
        private String addr;
        /** 命名空间 ID 列表（UUID 格式） */
        private List<String> namespaces;
        /** Nacos 认证用户名 */
        private String username;
        /** Nacos 认证密码 */
        private String password;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAddr() { return addr; }
        public void setAddr(String addr) { this.addr = addr; }
        public List<String> getNamespaces() { return namespaces; }
        public void setNamespaces(List<String> namespaces) { this.namespaces = namespaces; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}

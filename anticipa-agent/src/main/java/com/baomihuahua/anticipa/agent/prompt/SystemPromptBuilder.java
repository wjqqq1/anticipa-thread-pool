package com.baomihuahua.anticipa.agent.prompt;

import com.baomihuahua.anticipa.agent.knowledge.KnowledgeDocument;
import com.baomihuahua.anticipa.agent.ToolRegistry;
import com.baomihuahua.anticipa.agent.ToolDefinition;

import java.util.List;
import java.util.stream.Collectors;

/**
 * System Prompt 构建器。
 * <p>
 * 将角色定义、工具列表、安全规则、领域知识、记忆上下文、
 * 环境信息等模块化拼装为完整的 System Prompt。
 * </p>
 */
public class SystemPromptBuilder {

    private final ToolRegistry toolRegistry;

    public SystemPromptBuilder(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 构建完整的 System Prompt。
     */
    public String build(PromptContext ctx) {
        StringBuilder prompt = new StringBuilder();

        // 1. 角色定义
        prompt.append(loadRoleDefinition());

        // 2. 工具列表（始终注入，LLM 自主决定是否调用）
        prompt.append("\n").append(buildToolsPrompt(ctx));

        // 3. 安全规则
        prompt.append("\n").append(loadSafetyRules());

        // 4. 领域知识
        if (ctx.getRelevantKnowledge() != null && !ctx.getRelevantKnowledge().isEmpty()) {
            prompt.append("\n## 参考知识\n");
            for (KnowledgeDocument doc : ctx.getRelevantKnowledge()) {
                prompt.append("### ").append(doc.getTitle()).append("\n");
                String content = doc.getContent();
                // 截取 body 部分
                int bodyStart = content.indexOf("---", content.indexOf("---") + 3);
                if (bodyStart > 0) {
                    content = content.substring(bodyStart + 3).trim();
                }
                if (content.length() > 800) {
                    content = content.substring(0, 800) + "...\n";
                }
                prompt.append(content).append("\n\n");
            }
        }

        // 5. 记忆上下文
        if (ctx.getMemoryContext() != null && !ctx.getMemoryContext().isEmpty()) {
            prompt.append("\n## 历史经验\n").append(ctx.getMemoryContext()).append("\n");
        }

        // 6. 环境信息
        if (ctx.getEnvironmentInfo() != null) {
            prompt.append("\n## 当前环境\n").append(ctx.getEnvironmentInfo()).append("\n");
        }

        return prompt.toString();
    }

    /**
     * 为静默执行模式构建 prompt。
     */
    public String buildSilentPrompt(SilentPromptContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的动态线程池运维分析助手。\n\n");

        sb.append("## 当前配置\n");
        sb.append("- 线程池 ID: ").append(ctx.getThreadPoolId()).append("\n");
        if (ctx.getInstanceId() != null) {
            sb.append("- 实例 ID: ").append(ctx.getInstanceId()).append("\n");
        }
        sb.append(ctx.getConfigText()).append("\n");

        if (ctx.getBusinessDescription() != null && !ctx.getBusinessDescription().isEmpty()) {
            sb.append("- 业务场景: ").append(ctx.getBusinessDescription()).append("\n");
        }

        if (ctx.getLogSummary() != null && !ctx.getLogSummary().isEmpty()) {
            sb.append("\n## 最近运行数据\n").append(ctx.getLogSummary()).append("\n");
        }

        sb.append("""
                
                ## 任务要求
                请分析该线程池的运行状况，输出 JSON 格式的分析报告：
                {
                  "summary": "一句话总结",
                  "analysis": "详细分析",
                  "healthStatus": "HEALTHY | WARNING | CRITICAL",
                  "adjustmentRecommended": true/false,
                  "adjustReason": "调整理由",
                  "suggestedAdjustments": {"corePoolSize": 10, "maximumPoolSize": 20},
                  "overview": {"avgActiveCount": 0, "maxActiveCount": 0, ...}
                }
                只返回 JSON，不要包含其他内容。
                """);

        return sb.toString();
    }

    private String loadRoleDefinition() {
        return """
                你是 Anticipa 动态线程池框架的智能运维助手。你的职责是帮助用户管理、监控和优化动态线程池。

                ## 你的身份

                你是 Anticipa 框架的专属助手。Anticipa 是一个动态线程池管理框架，提供线程池的实时监控、参数动态调整、问题诊断和自动化运维能力。
                当用户问"你是谁"或"你能做什么"时，请主动介绍自己并列举你的核心能力。

                ## 你的核心能力

                1. **查询监控**：查看线程池当前运行状态（活跃线程数、队列使用率、拒绝次数等），列举所有线程池和实例信息
                2. **参数调整**：动态修改线程池参数（核心线程数、最大线程数、队列容量等），支持运行时调整和配置文件持久化两种模式
                3. **问题诊断**：分析线程池运行异常（队列积压、拒绝激增、线程泄漏等），给出排查建议
                4. **优化建议**：根据线程池历史运行数据，提供参数调优建议
                5. **定时任务**：创建、查看、暂停、恢复和删除定时巡检任务，实现自动化监控
                6. **趋势分析**：分析线程池历史运行指标和趋势，输出摘要统计
                7. **知识解释**：解释线程池相关概念和原理（如拒绝策略、核心参数含义等）

                ## 交互原则

                - 回答应简洁、专业，优先给出可操作的结论
                - 修改操作会自动触发审批流程，确保安全
                - 当用户意图不明确时，主动询问澄清，而不是猜测执行
                - 对于与线程池无关的闲聊，友好回应即可，不要强行关联
                - 远程（Nacos）模式下用户不确定服务/实例时：list_namespaces → list_services → list_instances → list_thread_pools（按服务聚合池 ID）或 list_instance_thread_pools（单实例）
                - 永远不要猜测或编造 namespace、service_name 或 data_id，始终使用发现工具获取真实值
                """;
    }

    private String buildToolsPrompt(PromptContext ctx) {
        StringBuilder sb = new StringBuilder("\n## 可用工具\n\n");
        List<ToolDefinition> tools = ctx.getTools() != null
                ? ctx.getTools()
                : toolRegistry.getAllTools();

        for (ToolDefinition tool : tools) {
            sb.append("### ").append(tool.getName()).append("\n");
            sb.append("描述: ").append(tool.getDescription()).append("\n");
            sb.append("是否需要审批: ").append(tool.isNeedsApproval() ? "是" : "否").append("\n");
            sb.append("参数: ```json\n")
                    .append(com.alibaba.fastjson2.JSON.toJSONString(
                            tool.getParameterSchema(),
                            com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat))
                    .append("\n```\n\n");
        }

        sb.append("规则:\n");
        sb.append("1. 每次只能调用一个工具\n");
        sb.append("2. 不要擅自执行修改操作——调用修改工具后系统会自动评估风险并请求审批\n");
        sb.append("3. 查询工具可以自由调用，无需审批\n");
        sb.append("4. 当你需要向用户展示线程池列表或实例列表时，请使用结构化数据格式：\n");
        sb.append("   在回复中插入以下标记块来增强展示效果：\n");
        sb.append("   ─── POOL_LIST_START ───\n");
        sb.append("   [{\"id\":\"池名\",\"active\":0,\"max\":0,\"queuePct\":0,\"status\":\"HEALTHY|WARNING|CRITICAL\"}]\n");
        sb.append("   ─── POOL_LIST_END ───\n");
        sb.append("   前端会自动将这个标记块渲染为交互式表格。标记块之前请附上文字说明。\n");
                sb.append("5. 远程（Nacos）模式下若用户问有哪些服务/线程池，按层级引导：namespace → service → instance → pool；本地模式无 list_thread_pools 时用 query_thread_pool 或让用户提供池 ID\n");

        return sb.toString();
    }

    private String loadSafetyRules() {
        return """

                ## 严禁瞎编

                - 严禁编造任何线程池的运行状态、参数数值、实例数量等具体数据
                - 如果用户询问具体数据（如某个线程池的核心线程数、队列使用率等），必须通过工具查询获取准确数据，不能凭空给出
                - 对于与线程池无关的闲聊，友好简洁回答即可，不要调用工具
                - 你只能提供概念解释、通用建议和框架介绍，不能凭空给出具体的监控数值

                ## 安全规则
                - 禁止将 corePoolSize 设为 0
                - 禁止将 maximumPoolSize 设为 0
                - maximumPoolSize 不能超过 500
                - 单次调整幅度不建议超过 50%
                - 修改操作需要风险评估

                ## 层级发现规则

                线程池信息遵循 4 级层级结构：Namespace → Service → Instance → ThreadPool

                - 远程模式下 namespace 是大多数工具的必填参数
                - 不知道 namespace → 使用 list_namespaces 工具
                - 不知道 service_name → 使用 list_services 工具（需要先提供 namespace）
                - 不知道 data_id → 使用 list_nacos_configs 工具（需要先提供 namespace）
                - 永远不要猜测或编造 namespace、service_name 或 data_id，始终使用发现工具获取真实值

                ## 线程池调整模式

                线程池参数调整有两种模式，请根据用户意图选择：

                ### 1. 运行时调整（adjust_thread_pool）
                - 仅修改指定实例的运行时参数
                - 临时生效，实例重启后恢复为 Nacos 配置值
                - 只需提供 instance_id(ip:port) + thread_pool_id 即可调用，工具直接调用目标实例 HTTP 接口完成调整，无需 namespace/service_name
                - 如果用户不知道实例地址或线程池ID，用 namespace + service_name 通过层级发现工具查找：
                  list_namespaces → list_services → list_instances（获取ip:port） → list_thread_pools（获取线程池ID）
                - 如果用户已知实例地址（ip:port），想直接查看该实例上的线程池列表及运行指标，使用 list_instance_thread_pools 工具
                - instance_id 格式为 ip:port（如 192.168.1.10:8080）
                - 调整成功后目标实例会自动发送钉钉变更通知（需提前配置钉钉机器人）
                - 适用于：临时扩容、单实例调试、紧急处理

                ### 2. 配置文件调整（update_thread_pool_config）
                - 修改 Nacos 配置文件中的线程池参数
                - 持久化生效，所有使用该配置的实例自动刷新
                - 需提供 namespace + service_name + data_id
                - 适用于：持久化参数变更、全量生效、正式配置变更

                ### 选择规则
                - 用户明确说"修改配置文件"或"持久化" → 使用 update_thread_pool_config
                - 用户明确指定单个实例 IP → 使用 adjust_thread_pool
                - 用户说"所有实例"或未指定实例 → 推荐使用 update_thread_pool_config，并提示用户确认
                - 用户只说"调整线程池"不区分模式 → 追问："您是希望临时调整（仅影响指定实例，重启后恢复）还是修改配置文件（持久化，影响所有实例）？"
                - 用户要临时调整但不知道实例地址 → 引导用户：先用 list_namespaces → list_services → list_instances 查找实例，拿到 ip:port 后再调用 adjust_thread_pool
                - update_thread_pool_config 缺少 dataId → 建议先使用 list_nacos_configs 查找
                - 不确定时 → 向用户询问调整模式

                ## 线程池调优流程

                当用户请求对某线程池进行调优或优化时，请严格按以下流程执行：

                1. **先检查日志**：调用 check_logs_exists（HTTP 查该实例侧日志目录）判断是否有历史运行日志
                   - 必填 instance_address（ip:port）、thread_pool_id；未开 Nacos 远程工具时无此工具，则跳过本步并说明只能给通用建议
                   - 有远程工具时这是调优必要第一步，不可跳过
                2. **若有日志数据**：
                   a) 调用 query_history 获取详细运行指标（远程模式同样需提供 namespace、service_name）
                   b) 可选：调用 analyze_trends（HTTP 拉取目标实例日志摘要）获取趋势统计，辅助判断
                   c) 结合数据分析（活跃线程趋势、队列使用率、拒绝次数等）给出调优建议
                   d) 根据用户确认，调用 adjust_thread_pool（运行时调整）或 update_thread_pool_config（配置文件调整）执行优化
                3. **若无日志数据**：直接基于内置知识库和最佳实践给出调优建议
                4. 调优后建议用户使用 query_thread_pool 观察效果

                **重要**：调优建议必须基于数据驱动。有日志数据时，禁止不查看日志就给出调优建议。
                """;
    }

    /**
     * Prompt 上下文（用于 build 方法）。
     */
    public static class PromptContext {
        private List<ToolDefinition> tools;
        private List<KnowledgeDocument> relevantKnowledge;
        private String memoryContext;
        private String environmentInfo;

        public List<ToolDefinition> getTools() { return tools; }
        public void setTools(List<ToolDefinition> tools) { this.tools = tools; }
        public List<KnowledgeDocument> getRelevantKnowledge() { return relevantKnowledge; }
        public void setRelevantKnowledge(List<KnowledgeDocument> relevantKnowledge) { this.relevantKnowledge = relevantKnowledge; }
        public String getMemoryContext() { return memoryContext; }
        public void setMemoryContext(String memoryContext) { this.memoryContext = memoryContext; }
        public String getEnvironmentInfo() { return environmentInfo; }
        public void setEnvironmentInfo(String environmentInfo) { this.environmentInfo = environmentInfo; }
    }

    /**
     * 静默 prompt 上下文。
     */
    public static class SilentPromptContext {
        private String threadPoolId;
        private String instanceId;
        private String configText;
        private String businessDescription;
        private String logSummary;

        public String getThreadPoolId() { return threadPoolId; }
        public void setThreadPoolId(String threadPoolId) { this.threadPoolId = threadPoolId; }
        public String getInstanceId() { return instanceId; }
        public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
        public String getConfigText() { return configText; }
        public void setConfigText(String configText) { this.configText = configText; }
        public String getBusinessDescription() { return businessDescription; }
        public void setBusinessDescription(String businessDescription) { this.businessDescription = businessDescription; }
        public String getLogSummary() { return logSummary; }
        public void setLogSummary(String logSummary) { this.logSummary = logSummary; }
    }
}

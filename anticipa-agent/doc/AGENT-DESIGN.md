# Anticipa Agent 模块优化设计文档

> 参考 Claude Code 开源架构设计及 Anthropic《Building agents that reach production systems with MCP》实践指南，结合 Anticipa 动态线程池场景，对 Agent 模块进行系统性升级。

---

## 一、现状分析

### 当前架构

```
AgentLoop（单循环，同步阻塞）
  → AIClient（OkHttp 调 OpenAI API）
    → 文本解析 TOOL_CALL:（非原生 Function Calling）
      → ToolRegistry（简单 Map 注册）
        → SafetyGuard（单规则检查）
          → ApprovalService（内存审批）
            → AuditStore（内存审计）
```

### 存在的问题

| 问题 | 现状 | 影响 |
|---|---|---|
| 工具调用方式 | 文本匹配 `TOOL_CALL:` 前缀 | 解析脆弱，依赖 LLM 严格遵循格式 |
| 上下文管理 | 全量消息列表无限增长 | Token 耗尽导致 API 报错 |
| 工具注入方式 | 全量工具定义塞入 System Prompt | Token 浪费，随着工具增多 schema 膨胀严重 |
| 错误处理 | 调用失败直接返回固定文案 | 无重试、无降级 |
| 会话管理 | 内存 ConcurrentHashMap | 重启丢失所有会话 |
| 安全模型 | 单条调整幅度规则 | 规则过少，无法覆盖复杂场景 |
| 流式响应 | 无 | 用户体验差，需等待全部生成完毕 |
| System Prompt | 硬编码字符串拼接 | 不可扩展、不可配置 |
| 工具并发 | 每次只调一个工具 | 多个查询操作无法并行，效率低 |
| 记忆/知识 | 无 | Agent 无法积累经验、无领域知识 |

---

## 二、目标架构（参考 Claude Code）

### 分层设计

借鉴 Claude Code 的分层架构，将 Agent 分为 4 层：

```
┌─────────────────────────────────────────────────────────┐
│                   API Layer（对外接口）                    │
│         AgentController / WebSocket Endpoint             │
├─────────────────────────────────────────────────────────┤
│                  Orchestrator（编排层）                    │
│   SessionManager · ContextManager · PermissionManager    │
├─────────────────────────────────────────────────────────┤
│                  AgentLoop（核心引擎）                     │
│   消息循环 · 工具调度 · 安全评估 · 错误恢复               │
├─────────────────────────────────────────────────────────┤
│                  Adapter Layer（适配层）                   │
│   LLM Adapter · Tool Executor · Memory Store             │
└─────────────────────────────────────────────────────────┘
```

### 模块内包结构

```
anticipa-agent/
├── core/                       ← 核心引擎
│   ├── AgentLoop.java          ← 主循环（重构）
│   ├── AgentOrchestrator.java  ← 编排器（新增）
│   └── model/                  ← 通用模型（Message, ToolCall, AgentRequest/Response）
│
├── tool/                       ← 工具系统（重构）
│   ├── ToolRegistry.java       ← 工具注册表
│   ├── ToolDefinition.java     ← 工具定义
│   ├── ToolResult.java         ← 工具执行结果
│   ├── ToolExecutor.java       ← 工具执行引擎（新增，支持并发）
│   ├── ToolCategory.java       ← 工具分类枚举（新增）
│   └── ToolSearchService.java  ← 工具按需检索（新增，参考 Anthropic Tool Search）
│
├── llm/                        ← LLM 适配（重构）
│   ├── LLMAdapter.java         ← LLM 适配器接口（替代 AIClient）
│   ├── OpenAIAdapter.java      ← OpenAI 兼容实现（支持 Function Calling）
│   └── dto/
│
├── context/                    ← 上下文管理（新增）
│   ├── ContextManager.java     ← 上下文管理器
│   ├── ContextWindow.java      ← 上下文窗口（Token 感知）
│   ├── MessageCompactor.java   ← 消息压缩器
│   └── ContextSummarizer.java  ← 上下文摘要生成器
│
├── memory/                     ← 记忆管理（新增）
│   ├── MemoryManager.java      ← 记忆管理器
│   ├── ShortTermMemory.java    ← 短期记忆（当前会话）
│   ├── LongTermMemory.java     ← 长期记忆（跨会话经验）
│   └── MemoryStore.java        ← 持久化接口
│
├── knowledge/                  ← 知识库（新增）
│   ├── KnowledgeService.java   ← 知识库服务
│   ├── KnowledgeDocument.java  ← 知识文档模型
│   └── KnowledgeRetriever.java ← 检索器接口
│
├── intent/                     ← 意图识别（新增）
│   ├── IntentRecognizer.java   ← 意图识别器
│   ├── Intent.java             ← 意图枚举
│   └── IntentRouter.java       ← 意图路由
│
├── permission/                 ← 权限系统（重构，替代 safety/approval）
│   ├── PermissionManager.java  ← 权限管理器
│   ├── PermissionRule.java     ← 权限规则
│   ├── RiskEvaluator.java      ← 风险评估器
│   └── ApprovalService.java    ← 审批服务
│
├── audit/                      ← 审计（增强）
│   └── AuditStore.java
│
├── session/                    ← 会话管理（增强）
│   └── SessionManager.java
│
├── prompt/                     ← Prompt 工程（新增）
│   ├── SystemPromptBuilder.java← 系统提示词构建器
│   └── PromptTemplate.java     ← 提示词模板
│
└── config/
    ├── AgentProperties.java
    └── AgentAutoConfiguration.java
```

---

## 三、核心改造项

### 3.1 Agent Loop 重构

**参考 Claude Code 的六阶段循环：**

当前的 `AgentLoop.process()` 只是简单的 for 循环，改造为多阶段流水线：

```
每一轮 Turn：
  1. 前置处理（Pre-Request）
     ├── 上下文压缩检查：Token 是否接近上限
     ├── 记忆注入：从 MemoryManager 加载相关记忆
     └── 知识增强：从 KnowledgeService 检索相关文档

  2. LLM 调用（API Call）
     ├── 使用原生 Function Calling（不再文本解析）
     ├── 支持流式输出（SSE）
     └── 请求超时 + 重试策略

  3. 响应处理（Response Processing）
     ├── 判断是否为 end_turn（最终回答）
     ├── 判断是否为 tool_use（工具调用）
     └── 判断是否需要继续（needsFollowUp）

  4. 工具执行（Tool Execution）
     ├── 权限检查 → 安全评估 → 审批（如需）
     ├── 只读工具并行执行（isConcurrencySafe=true）
     ├── 写入工具串行执行
     └── 执行结果写入消息列表

  5. 后置处理（Post-Turn）
     ├── 审计记录
     ├── 记忆更新
     └── 衰减检测（连续低效迭代则提前终止）

  6. 循环控制
     ├── 最大轮次检查
     ├── Token 预算检查
     └── 上下文溢出恢复（先压缩，再重试）
```

**关键设计：不可变参数 vs 可变状态**

```java
// 不可变：贯穿整个请求生命周期
public record QueryParams(
    String sessionId,
    String userId,
    String userMessage,
    SessionContext context
) {}

// 可变：每轮更新，原子替换
public class AgentState {
    private List<Message> messages;
    private int turnCount;
    private int totalTokensUsed;
    private String lastToolResult;
    // 每次更新都是全量替换，不做部分修改
}
```

### 3.2 原生 Function Calling 支持

**现状问题：** 当前通过文本匹配 `TOOL_CALL:` 解析工具调用，这依赖 LLM 严格遵循自定义格式，容易失败。

**改造方案：** 使用 OpenAI 标准的 Function Calling 协议。

```java
// LLM 请求中携带工具定义（JSON Schema）
{
  "model": "gpt-4",
  "messages": [...],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "query_thread_pool",
        "description": "查询指定线程池的运行时状态",
        "parameters": {
          "type": "object",
          "properties": {
            "threadPoolId": { "type": "string", "description": "线程池 ID" }
          },
          "required": ["threadPoolId"]
        }
      }
    }
  ]
}

// LLM 响应中直接返回结构化的工具调用
{
  "choices": [{
    "message": {
      "role": "assistant",
      "tool_calls": [{
        "id": "call_abc123",
        "type": "function",
        "function": {
          "name": "query_thread_pool",
          "arguments": "{\"threadPoolId\": \"onethread-producer\"}"
        }
      }]
    }
  }]
}
```

**好处：**
- 工具调用由 LLM 原生支持，解析可靠性大幅提升
- 支持单次返回多个工具调用（并行执行的前提）
- 参数类型由 JSON Schema 约束，减少错误

### 3.3 Tool Search —— 工具按需加载（参考 Anthropic MCP 实践指南）

> 来源：Anthropic《Building agents that reach production systems with MCP》博客，核心观点：**不要把所有工具定义一股脑塞进上下文，按需加载能减少 85% 以上的工具定义 Token 消耗。**

**现状问题：** 当前 `ToolRegistry.buildToolsPrompt()` 会将所有注册工具的完整定义拼入 System Prompt。以 GitHub MCP 服务器为例（43 个工具），仅工具定义就占 55,000 tokens，而用户可能只需要其中 2-3 个工具。

随着 Anticipa 工具数量增加（查询、调整、诊断、批量操作、Web 线程池、历史记录、回滚等），同样会面临 schema 膨胀问题。

**设计方案：意图驱动的 Tool Search**

将意图识别和工具检索结合，根据用户意图只加载相关工具：

```
用户: "producer 线程池队列快满了"
  ↓
IntentRecognizer: Intent.DIAGNOSE
  ↓
ToolSearchService.search(Intent.DIAGNOSE, "producer 队列满")
  ↓
返回: [query_thread_pool, query_metrics, get_adjust_history]  ← 只有 3 个工具
  ↓
System Prompt 中只包含这 3 个工具的定义（而不是全部 10+ 个）
```

**工具按意图分组（而非按 API 分）：**

Anthropic 博客明确指出：**工具要按意图分组，别按 API 分。** 当前工具是逐个注册的（按 API 粒度），应改为按用户意图分组：

```java
public enum ToolCategory {
    QUERY,          // 查询类：查状态、查指标、查配置
    ADJUST,         // 调整类：调参数、批量调、回滚
    DIAGNOSE,       // 诊断类：查历史、查告警、分析趋势
    SYSTEM          // 系统类：实例信息、健康检查
}

@Data
@Builder
public class ToolDefinition {
    private String name;
    private String description;
    private ToolCategory category;        // 新增：工具分类
    private boolean modification;
    private boolean needsApproval;
    private boolean concurrencySafe;      // 新增：是否可并发（只读工具 = true）
    private Map<String, Object> parameterSchema;
    private Function<Map<String, Object>, ToolResult> executor;
}
```

**ToolSearchService 核心实现：**

```java
public class ToolSearchService {

    private final ToolRegistry toolRegistry;

    /**
     * 根据意图和用户输入，检索最相关的工具子集
     * 替代全量注入，减少 System Prompt 的 Token 消耗
     */
    public List<ToolDefinition> search(Intent intent, String userMessage) {
        // 第一层：按意图过滤分类
        Set<ToolCategory> categories = intentToCategories(intent);
        List<ToolDefinition> candidates = toolRegistry.getAllTools().stream()
                .filter(t -> categories.contains(t.getCategory()))
                .collect(Collectors.toList());

        // 第二层：关键词相关性排序（可选，工具数量少时可跳过）
        if (candidates.size() > 5) {
            candidates = rankByRelevance(candidates, userMessage);
        }

        return candidates;
    }

    private Set<ToolCategory> intentToCategories(Intent intent) {
        return switch (intent) {
            case QUERY    -> Set.of(ToolCategory.QUERY);
            case ADJUST   -> Set.of(ToolCategory.QUERY, ToolCategory.ADJUST);  // 调整时也需要先查
            case DIAGNOSE -> Set.of(ToolCategory.QUERY, ToolCategory.DIAGNOSE);
            case OPTIMIZE -> Set.of(ToolCategory.QUERY, ToolCategory.ADJUST, ToolCategory.DIAGNOSE);
            case BATCH_OPERATE -> Set.of(ToolCategory.QUERY, ToolCategory.ADJUST);
            default       -> Set.of(ToolCategory.values());  // EXPLAIN/CHAT 不需要工具
        };
    }
}
```

**Token 节省预估：**

| 场景 | 全量注入（现状） | Tool Search（改造后） | 节省 |
|---|---|---|---|
| 10 个工具，用户查询状态 | ~5,000 tokens | ~1,500 tokens（3 个工具） | **70%** |
| 20 个工具，用户调整参数 | ~10,000 tokens | ~3,000 tokens（6 个工具） | **70%** |
| 20 个工具，复杂诊断 | ~10,000 tokens | ~4,000 tokens（8 个工具） | **60%** |

随工具总量增多，节省比例越大。Anthropic 实测在 43 个工具场景下节省超过 85%。

**与 IntentRouter 的协作关系：**

```
IntentRecognizer
  → 输出 Intent
    → ToolSearchService.search(intent, message)
      → 返回工具子集
        → SystemPromptBuilder 只注入这些工具
          → AgentLoop 用精简工具集调用 LLM
```

意图识别决定"用户想干什么"，Tool Search 决定"给 LLM 看哪些工具"。两者天然配合。

### 3.4 原生 Function Calling + Tool Search 的联合工作

当 3.2 的原生 Function Calling 和 3.3 的 Tool Search 同时生效时，LLM 请求体变为：

```json
{
  "model": "gpt-4",
  "messages": [...],
  "tools": [
    // ← 只包含 Tool Search 筛选出的工具，而非全量
    {
      "type": "function",
      "function": {
        "name": "query_thread_pool",
        "description": "查询指定线程池的运行时状态",
        "parameters": { ... }
      }
    },
    {
      "type": "function",
      "function": {
        "name": "query_metrics",
        "description": "查询线程池的运行指标（活跃线程、队列使用率等）",
        "parameters": { ... }
      }
    }
  ]
}
```

两者叠加效果：Function Calling 保证**调用可靠性**，Tool Search 保证 **Token 经济性**。

### 3.5 上下文管理（参考 Claude Code 五阶段压缩流水线）

**这是最关键的新增模块。** 当前消息列表无限增长，会导致 Token 超限。

Claude Code 采用五阶段压缩流水线，按成本从低到高依次执行：

```
Stage 1: Tool Result Budget（工具结果裁剪 + 程序化处理）
  └── 对超长的工具返回结果进行截断，保留关键数据
      示例：线程池快照数据超过 2000 Token → 只保留摘要字段
  └── 参考 Anthropic「程序化工具调用」思想（实测减少约 37% Token）：
      工具返回的原始数据不直接丢回给 LLM，而是先在内部做聚合、过滤、计算，
      只把最终结论性数据返回上下文。
      示例：query_all_pools 返回 20 个线程池的完整数据
        → 内部过滤 → 只保留异常/高负载的 3 个线程池摘要
        → 返回给 LLM 的数据从 ~8000 tokens 降到 ~800 tokens

Stage 2: Snip Compact（早期消息丢弃）
  └── 丢弃距当前最远的历史消息（保留 system prompt 和最近 N 轮）

Stage 3: Microcompact（精准清理）
  └── 针对特定类型消息做清理：
      - 已执行的工具调用：只保留结果摘要
      - 重复的查询结果：只保留最新一次

Stage 4: Context Collapse（上下文坍缩）
  └── 调用 LLM 对历史对话生成摘要，替换原始消息
      "到目前为止：用户询问了 producer 线程池状态，发现活跃线程 = 最大线程，
       队列使用率 85%，已建议调大 maximumPoolSize 从 10 到 15。"

Stage 5: Auto-Compact（自动压缩）
  └── 当剩余 Token < 阈值时触发，强制执行 Stage 4
      带断路器：连续失败 3 次则停止，避免死循环
```

**Java 实现核心接口：**

```java
public class ContextManager {
    private final int maxTokenBudget;       // 最大 Token 预算（如 128000）
    private final int compactThreshold;     // 触发压缩的阈值（如剩余 < 13000）
    private final LLMAdapter llmAdapter;    // 用于生成摘要

    /**
     * 每次调用 LLM 前执行，确保消息列表在 Token 预算内
     */
    public List<Message> prepareMessages(List<Message> messages) {
        int currentTokens = estimateTokens(messages);
        if (currentTokens <= maxTokenBudget) return messages;

        // 按成本从低到高依次尝试
        messages = trimToolResults(messages);           // Stage 1
        if (withinBudget(messages)) return messages;

        messages = snipOldMessages(messages);           // Stage 2
        if (withinBudget(messages)) return messages;

        messages = microcompact(messages);              // Stage 3
        if (withinBudget(messages)) return messages;

        messages = collapseContext(messages);            // Stage 4
        return messages;
    }
}
```

### 3.6 权限系统重构（参考 Claude Code Deny-First 模型）

当前 `SafetyGuard` 只有一条"调整幅度"规则。参考 Claude Code 的多层权限模型：

```
权限判定优先级（从高到低）：
  deny 规则 > ask 规则（需审批） > allow 规则

权限规则来源（按优先级合并）：
  1. 系统内置规则（硬编码，如：禁止将 corePoolSize 设为 0）
  2. 应用配置规则（application.yml 中定义）
  3. 运行时动态规则（管理员通过 API 添加）
```

**具体规则示例：**

```yaml
anticipa:
  agent:
    permissions:
      deny:
        - tool: "adjust_thread_pool"
          condition: "params.corePoolSize == 0"
          reason: "禁止将核心线程数设为 0"
        - tool: "adjust_thread_pool"
          condition: "params.maximumPoolSize > 500"
          reason: "最大线程数不能超过 500"
      ask:
        - tool: "adjust_thread_pool"
          condition: "changeRatio > 0.3"
          reason: "调整幅度超过 30%，需人工确认"
        - tool: "batch_adjust"
          reason: "批量调整操作始终需要审批"
      allow:
        - tool: "query_*"
          reason: "所有查询操作自动放行"
```

### 3.7 记忆管理

#### 3.7.1 业务场景声明（businessType）—— 已实现

在使用 AI 模块之前，建议在声明线程池时标明其业务场景。当前 `ThreadPoolExecutorProperties` 已新增 `businessType` 字段：

```java
// ThreadPoolExecutorProperties 中已实现
public class BusinessType {

    public enum Type {
        CPU_INTENSIVE,               // CPU 密集型（计算为主）
        IO_INTENSIVE,                // IO 密集型（数据库/网络调用为主）
        MIXED,                       // 混合型
        SCHEDULED_TASK,              // 定时任务
        HIGH_CONCURRENCY_SHORT_TASK, // 高并发短任务
        CUSTOM                       // ← 用户自定义（覆盖所有场景）
    }

    private Type type;
    private String customDescription;  // 当 type=CUSTOM 时填写

    /** 供 AI 理解业务场景的完整文本 */
    public String toDisplayString() {
        // 自定义描述的优先级高于预设枚举
    }
}
```

对应 YAML 配置：

```yaml
onethread:
  executors:
    - thread-pool-id: onethread-producer
      core-pool-size: 12
      maximum-pool-size: 24
      business-type:
        type: CUSTOM
        custom-description: "消息推送服务，IO 密集型，高峰期 14:00-17:00"
    - thread-pool-id: onethread-consumer
      business-type:
        type: CPU_INTENSIVE    # 简写形式保持兼容
```

`businessType` 的作用范围：
- **Agent 查询内存时**：优先检索同类业务场景的历史记忆
- **Agent 调优建议时**：根据业务场景匹配对应的最佳实践知识
- **Agent 做决策时**：综合当前配置 + 业务场景 + 历史经验给出判断

#### 3.7.2 长期记忆存储格式

长期记忆采用 **结构化 Markdown 文件**存储，通过 YAML frontmatter 标记元数据，正文自由描述。

**目录结构：**

```
data/
├── memory/                    # 长期记忆
│   ├── producer-adjust-20260426.md
│   ├── consumer-diagnose-20260425.md
│   └── ...
└── knowledge/                 # 知识库（3.8 节详述）
    └── ...
```

**文件命名规范：** `{threadPoolId}-{action}-{YYYYMMDD}.md`

**文件格式：**

```markdown
---
title: producer 线程池高峰期调优记录
type: memory                               # memory（记忆）/ knowledge（知识）
date: 2026-04-26
tags: [producer, IO密集型, 高峰期, 调优]
threadPoolId: onethread-producer
businessType: IO_INTENSIVE                 # 线程池业务场景
action: adjust                             # 操作类型：adjust/diagnose/query
severity: high                             # 重要程度：low/medium/high
---

# producer 线程池高峰期调优记录

## 现象
队列使用率 92%，活跃线程数 = 最大线程数 24，任务拒绝率上升至 3%。

## 调整
- maximumPoolSize: 24 → 36
- queueCapacity: 10000 → 20000

## 效果
队列使用率降至 45%，响应时间从 2.3s 降至 1.1s，拒绝率归零。

## 业务上下文
producer 为消息推送服务，IO 密集型业务，高峰期集中在 14:00-17:00。
当前服务器 CPU 16 核，内存 32G。

## 经验总结
IO 密集型线程池在高峰期，最大线程数建议放宽到 CPU 核数的 2~2.5 倍。
```

#### 3.7.3 文件级 MemoryStore

实现 `MemoryStore`，将长期记忆读写映射到文件系统：

```java
public class FileMemoryStore implements MemoryStore {

    private final Path memoryDir = Path.of("data/memory");

    /**
     * 将本轮对话提炼为记忆文件写入磁盘
     */
    public void save(MemoryEntry entry) {
        String filename = entry.getThreadPoolId() + "-"
                + entry.getAction() + "-"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + ".md";
        String content = buildMarkdown(entry);
        Files.writeString(memoryDir.resolve(filename), content);
    }

    /**
     * 关键词检索记忆文件（grep-style）
     * @param keywords 从用户提问中提取的关键词
     * @param context  当前上下文（含 threadPoolId、businessType 等）
     * @param topK     返回前 K 条
     */
    public List<ScoredMemory> search(List<String> keywords, SearchContext context, int topK) {
        // 1. 遍历 memoryDir 下所有 .md 文件
        // 2. 对每个文件统计关键词命中情况
        // 3. 按权重规则计算得分（见评分算法）
        // 4. 按得分排序，返回 Top-K
    }

    private String buildMarkdown(MemoryEntry entry) {
        return "---\n"
                + "title: " + entry.getTitle() + "\n"
                + "type: memory\n"
                + "date: " + LocalDate.now() + "\n"
                + "tags: [" + String.join(", ", entry.getTags()) + "]\n"
                + "threadPoolId: " + entry.getThreadPoolId() + "\n"
                + (entry.getBusinessType() != null ? "businessType: " + entry.getBusinessType() + "\n" : "")
                + "action: " + entry.getAction() + "\n"
                + "severity: " + entry.getSeverity() + "\n"
                + "---\n\n"
                + entry.getContent();
    }
}
```

#### 3.7.4 记忆管理全景

```
MemoryManager
  ├── ShortTermMemory（短期记忆）
  │   ├── 当前会话 List<Message>
  │   └── 本轮关注的 threadPoolId 列表
  │
  └── FileMemoryStore（长期记忆 → 文件系统）
      ├── data/memory/*.md          ← 每次调优操作后写入一条
      └── search(keywords, context) ← 每轮 LLM 调用前检索
```

记忆写入时机：
- 每次调优操作完成后
- 每次诊断分析有明确结论时
- 异常告警分析完成后

记忆读取时机：
- 每轮 LLM 调用前，检索相关记忆注入 System Prompt

### 3.8 知识库（Markdown 文件 + grep 检索）

> 参考 Anthropic MCP + Skills 分层思想：**MCP 管「能力」（原子操作），Skills 管「编排」。** 映射到 Anticipa：工具（Tools）提供原子能力，知识库（Knowledge）提供编排思路——告诉 Agent 业界在类似场景下怎么做。

> 存储方案选用 **结构化 Markdown + grep 式关键词检索**，零外部依赖，文件即数据，可直接用 Git 追踪变更。

#### 3.8.1 存储格式与目录结构

```
data/knowledge/
├── best-practice-cpu-intensive.md        # CPU 密集型最佳实践
├── best-practice-io-intensive.md         # IO 密集型最佳实践
├── best-practice-mixed.md               # 混合型业务配置建议
├── best-practice-strategy-rejected.md    # 拒绝策略选择指南
├── best-practice-strategy-queue.md       # 队列满时的处理策略
├── best-practice-monitoring.md           # 线程池监控指标解读
├── best-practice-common-pitfalls.md      # 常见配置陷阱
├── tuning-guide-core-size.md             # 核心线程数调参指南
├── tuning-guide-queue-size.md            # 队列容量调参指南
├── diagnose-high-cpu.md                  # 高 CPU 场景诊断
├── diagnose-task-rejection.md            # 任务拒绝问题排查
└── diagnose-uneven-load.md               # 负载不均衡诊断
```

**文件命名规范：** `{category}-{topic}.md`

**标准格式（所有知识库文件统一模板）：**

```markdown
---
title: IO 密集型线程池最佳实践
type: knowledge
category: best-practice
tags: [IO密集型, 线程池, 配置建议]
businessType: IO_INTENSIVE
priority: P0                              # P0=核心/P1=推荐/P2=参考
source: industry                          # industry / official / community
---

# IO 密集型线程池最佳实践

## 适用场景
适用于数据库访问、RPC 调用、HTTP 请求等以 IO 等待为主的业务。

## 核心建议
- corePoolSize = CPU 核数 × 2（或 CPU 核数 × 2 + 1）
- maximumPoolSize = CPU 核数 × 4（不超过 200，视硬件而定）
- 队列建议使用 ResizableCapacityLinkedBlockingQueue
- 队列容量 = 预估 QPS × 响应时间 SLA（秒）
- keepAliveTime 建议 60~120 秒
- 拒绝策略建议 CallerRunsPolicy（降级而非丢弃）

## 原理说明
IO 密集型线程在大部分时间处于等待 IO 状态，CPU 利用率不高。
增加线程数能提高 CPU 利用率，但过多会导致上下文切换开销反超收益。

## 注意事项
- 避免线程数超过数据库连接池上限
- 监控实际活跃线程数，若长期等于 maximumPoolSize 则需扩容
```

#### 3.8.2 内置知识库内容

系统预置以下 12 篇知识文档（打包在 JAR 内 classpath 资源，首次运行自动释放到 `data/knowledge/`）：

| 文件名 | 标题 | 说明 |
|---|---|---|
| `best-practice-cpu-intensive.md` | CPU 密集型最佳实践 | corePoolSize=CPU+1，小队列 |
| `best-practice-io-intensive.md` | IO 密集型最佳实践 | corePoolSize=CPU×2，大队列 |
| `best-practice-mixed.md` | 混合型业务配置建议 | 折中方案 + 监控调优 |
| `best-practice-strategy-rejected.md` | 拒绝策略选择指南 | Abort/CallerRuns/Discard 对比 |
| `best-practice-strategy-queue.md` | 队列满处理策略 | 扩容 vs 降级 vs 限流 |
| `best-practice-monitoring.md` | 监控指标解读 | 活跃线程、队列深度、拒绝率阈值 |
| `best-practice-common-pitfalls.md` | 常见配置陷阱 | 线程池过大 OOM、队列无限等 |
| `tuning-guide-core-size.md` | 核心线程数调参 | 如何根据压测确定基准值 |
| `tuning-guide-queue-size.md` | 队列容量调参 | 结合 QPS 和响应时间计算 |
| `diagnose-high-cpu.md` | 高 CPU 场景诊断 | 线程数过多致频繁上下文切换 |
| `diagnose-task-rejection.md` | 任务拒绝排查 | 队列满 + 线程数满的多种原因 |
| `diagnose-uneven-load.md` | 负载不均衡诊断 | 多线程池间负载差异分析 |

#### 3.8.3 搜索引擎 —— grep 式加权评分

不再用 TF-IDF，改用 **行级关键词匹配 + 加权评分**：

```java
public class FileKnowledgeRetriever {

    private final Path knowledgeDir = Path.of("data/knowledge");
    private final Path memoryDir = Path.of("data/memory");

    public List<ScoredDocument> search(List<String> queries, SearchContext context, int topK) {
        List<ScoredDocument> results = new ArrayList<>();
        // 1. 检索知识库
        results.addAll(searchInDir(knowledgeDir, queries, context, "knowledge"));
        // 2. 检索记忆库
        results.addAll(searchInDir(memoryDir, queries, context, "memory"));
        // 3. 按得分排序
        results.sort(Comparator.comparingDouble(ScoredDocument::getScore).reversed());
        return results.subList(0, Math.min(topK, results.size()));
    }

    private List<ScoredDocument> searchInDir(Path dir, List<String> queries,
                                              SearchContext ctx, String source) {
        List<ScoredDocument> results = new ArrayList<>();
        Files.walk(dir, 1).filter(p -> p.toString().endsWith(".md")).forEach(file -> {
            String content = Files.readString(file);
            double score = calculateScore(content, queries, ctx, source);
            if (score > 0) {
                results.add(new ScoredDocument(file, score, parseFrontmatter(content)));
            }
        });
        results.sort(Comparator.comparingDouble(ScoredDocument::getScore).reversed());
        return results;
    }

    private double calculateScore(String content, List<String> queries,
                                   SearchContext ctx, String source) {
        // 关键词命中率
        long matchCount = queries.stream()
                .filter(q -> content.toLowerCase().contains(q.toLowerCase()))
                .count();
        if (matchCount == 0) return 0;
        double score = (double) matchCount / queries.size();

        // ── 匹配位置加权 ──
        if (matchInYamlTag(content, queries))      score *= 2.0;  // tags命中
        else if (matchInYamlTitle(content, queries)) score *= 1.8; // title命中
        else if (matchInHeading(content, queries))  score *= 1.5; // 标题行命中

        // ── 来源加权：知识库 > 记忆（默认） ──
        if ("knowledge".equals(source)) score *= 1.3;

        // ── 上下文提权：记忆可以反超知识 ──
        if (ctx.getThreadPoolId() != null && content.contains(ctx.getThreadPoolId()))
            score *= 2.0;                          // 同一线程池的历史记录更有价值
        if (ctx.getBusinessType() != null && content.contains(ctx.getBusinessType().name()))
            score *= 1.5;                          // 同一业务类型的经验更相关
        if ("memory".equals(source) && hasTemporalQuery(queries))
            score *= 1.3;                          // 用户查历史 → 记忆权重提高

        return score;
    }
}
```

#### 3.8.4 优先级效果场景

```
场景 A：用户问通用问题
用户: "IO 密集型线程池怎么配置？"
知识库 → IO密集型最佳实践.md     得分 1.95（tags×2 × 知识源×1.3 = 2.6）
记忆库 → （无匹配）
→ 返回知识库结果 ✅（权威性优先）

场景 B：用户问自己线程池的问题
用户: "producer 线程池队列满了怎么办"
知识库 → 队列满处理策略.md       得分 1.95（heading×1.5 × 知识源×1.3）
记忆库 → producer-adjust-0426.md  得分 2.60（tags×2 × 记忆源×1.0 × threadPoolId×2.0
                                                          = 4.0 ÷ 2 另有businessType×1.5）
→ 返回记忆库（命中同一线程池，场景更匹配）

场景 C：用户回溯历史
用户: "昨天 producer 调整效果怎么样"
知识库 → IO密集型最佳实践.md     得分 1.30
记忆库 → producer-adjust-0425.md 得分 2.34（heading×1.5 × 时间提权×1.3 × threadPoolId×2.0）
→ 返回记忆库（时间关键词主动提高记忆权重）
```

**核心原则：** 知识库的通用最佳实践是默认首选；但当上下文高度匹配（同一线程池、同一业务类型、时间回溯）时，记忆可以反超知识。Agent 最终综合两者给出分析。

### 3.9 意图识别

在 AgentLoop 进入主循环之前，先判断用户意图，走不同的处理路径：

```java
public enum Intent {
    QUERY,          // 查询线程池状态
    ADJUST,         // 调整线程池参数
    DIAGNOSE,       // 诊断问题
    OPTIMIZE,       // 请求优化建议
    EXPLAIN,        // 解释概念
    CHAT,           // 闲聊/无关问题
    BATCH_OPERATE   // 批量操作
}
```

**识别方式：** 两阶段

```
1. 快速规则匹配（关键词）
   "查看/查询/状态" → QUERY
   "调整/修改/设置" → ADJUST
   "为什么/原因/问题" → DIAGNOSE

2. 规则未命中 → 交给 LLM 判断（附带 Intent 枚举描述）
```

**意图路由：**

```java
public class IntentRouter {
    public AgentResponse route(Intent intent, AgentRequest request) {
        return switch (intent) {
            case QUERY    -> agentLoop.process(request, queryOnlyTools());
            case ADJUST   -> agentLoop.process(request, allTools());
            case DIAGNOSE -> agentLoop.processWithKnowledge(request);  // 额外注入诊断知识
            case EXPLAIN  -> directLLMAnswer(request);                 // 不需要工具，直接回答
            case CHAT     -> directLLMAnswer(request);
            default       -> agentLoop.process(request, allTools());
        };
    }
}
```

### 3.10 System Prompt 模块化

当前是硬编码拼接字符串。改为模板化、可配置：

```java
public class SystemPromptBuilder {

    public String build(PromptContext ctx) {
        StringBuilder prompt = new StringBuilder();

        // 1. 角色定义
        prompt.append(loadTemplate("role"));

        // 2. 工具列表（由 Tool Search 按需筛选，不再全量注入）
        prompt.append(toolSearchService.buildFilteredToolsPrompt(ctx.getIntent(), ctx.getUserMessage()));

        // 3. 安全规则
        prompt.append(loadTemplate("safety-rules"));

        // 4. 领域知识（按意图注入）
        if (ctx.getRelevantKnowledge() != null) {
            prompt.append("\n## 参考知识\n");
            prompt.append(ctx.getRelevantKnowledge());
        }

        // 5. 记忆上下文（从长期记忆注入）
        if (ctx.getMemoryContext() != null) {
            prompt.append("\n## 历史经验\n");
            prompt.append(ctx.getMemoryContext());
        }

        // 6. 环境信息（当前线程池概况）
        prompt.append("\n## 当前环境\n");
        prompt.append(ctx.getEnvironmentInfo());

        return prompt.toString();
    }
}
```

### 3.11 错误处理与恢复策略

参考 Claude Code 的级联恢复机制：

```
LLM 调用失败
  ├── 网络超时 → 指数退避重试（最多 3 次）
  ├── Token 超限（prompt_too_long）
  │   ├── 第一次：执行 Context Collapse（摘要压缩）
  │   ├── 第二次：执行 Snip Compact（丢弃早期消息）
  │   └── 第三次：返回错误提示
  ├── 速率限制（429）→ 等待 Retry-After 后重试
  └── 服务不可用（500/503）→ 短暂等待后重试

工具执行失败
  ├── 工具不存在 → 告知 LLM 可用工具列表
  ├── 参数错误 → 告知 LLM 参数格式要求
  └── 执行异常 → 将错误信息反馈给 LLM，让其决定下一步

衰减检测（防止死循环）
  └── 连续 3 轮 LLM 返回相同/无效内容 → 提前终止并给出当前阶段性结论
```

### 3.12 流式响应支持

当前是同步阻塞等 LLM 完整返回。改为 SSE 流式推送：

```java
// Controller 层
@GetMapping(value = "/agent/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chatStream(@RequestBody AgentRequest request) {
    SseEmitter emitter = new SseEmitter(300_000L);
    agentOrchestrator.processStream(request, event -> {
        emitter.send(SseEmitter.event()
            .name(event.getType())     // "thinking" / "tool_call" / "tool_result" / "answer"
            .data(event.getPayload()));
    });
    return emitter;
}
```

**事件类型：**

| type | 说明 | payload 示例 |
|---|---|---|
| `thinking` | Agent 思考中 | `"正在分析 producer 线程池状态..."` |
| `tool_call` | 发起工具调用 | `{"tool": "query_thread_pool", "params": {...}}` |
| `tool_result` | 工具返回结果 | `{"success": true, "data": {...}}` |
| `approval_required` | 需要审批 | `{"requestId": "...", "riskLevel": "HIGH"}` |
| `answer` | 最终回答片段 | `"建议将核心线程数从 5 调整到 8..."` |
| `done` | 结束 | `{"totalTokens": 1234, "turns": 3}` |

---

### 3.13 线程池运行日志模块 —— 已实现

#### 3.13.1 存储方案

文件存储，与知识库/记忆库一致的设计哲学。

**文件命名：** `data/logs/{threadPoolId}/{yyyyMMdd}.log`

**格式：** 每行一条 JSON 记录（JSON Lines 格式，方便追加和 grep）：

```json
{"p":"producer","t":1714118400000,"c":12,"m":24,"s":18,"a":15,"l":22,"ct":1234567,"qs":850,"qc":10000,"qr":9150,"rc":0,"rh":"CallerRunsPolicy"}
```

**目录结构：**
```
data/logs/
├── onethread-producer/
│   ├── 20260426.log
│   └── 20260427.log
└── onethread-consumer/
    └── 20260426.log
```

#### 3.13.2 配置项

```yaml
anticipa:
  log:
    enabled: false                     # 运行日志开关
    record-interval-seconds: 10        # 记录频率（与监控频率一致）
    retention-days: 7                  # 日志保留天数
    compress: true                     # 是否压缩旧日志（.gz）
    store-path: data/logs              # 存储路径
    query:
      max-window-minutes: 60           # AI 单次查询最大时间窗口
      default-window-minutes: 30       # AI 默认查询时间窗口
```

#### 3.13.3 AI 查询性能平衡策略

| 方案 | 说明 | 适用场景 |
|---|---|---|
| **全文扫描** | 直接读取对应日期文件，grep 时间范围 | 默认，适合 1 天内数据 |
| **分钟聚合** | 每分钟聚合成一条均值记录 | 查询范围 > 1 小时 |
| **小时聚合** | 只加载小时级聚合数据 | 历史趋势分析 |

#### 3.13.4 实现状态

- `ThreadPoolLogRecord.java` — 日志数据模型 ✅
- `ThreadPoolLogStore.java` — 文件存储与查询（支持 RAW/MINUTE/HOUR 三级聚合）✅
- `ThreadPoolLogConfig.java` — 配置属性 ✅
- `ThreadPoolMonitor.java` — 增强的采集与持久化 ✅
- `ThreadPoolLogController.java` — REST 查询接口 ✅

### 3.14 智能定时运维系统 —— 已实现

#### 3.14.1 特性总览

```
┌──────────────────────────────────────────────────────────┐
│                    智能定时运维系统                         │
├──────────────────────────────────────────────────────────┤
│  UI层                                                     │
│  ├── 定时任务配置页面（CRUD 表格 + 表单）                   │
│  ├── 执行历史页面（日志 + 分析结果查看）                    │
│  └── AI 聊天界面集成（自然语言创建任务）                    │
├──────────────────────────────────────────────────────────┤
│  业务层                                                   │
│  ├── ScheduledTaskService —— 定时任务的 CRUD              │
│  ├── TaskScheduler —— 定时调度引擎，触发 Agent 执行        │
│  ├── TaskExecutionService —— 单次任务执行全流程编排        │
│  └── AgentReportService —— 分析报告生成与通知              │
├──────────────────────────────────────────────────────────┤
│  数据层                                                   │
│  ├── ScheduledTask —— 定时任务模型                        │
│  ├── TaskExecutionLog —— 执行日志模型                     │
│  ├── ThreadPoolLogRecord —— 线程池运行日志数据模型         │
│  └── ThreadPoolLogStore —— 运行日志文件存储               │
├──────────────────────────────────────────────────────────┤
│  基础设施                                                 │
│  ├── ThreadPoolMonitor（增强）→ 新增日志记录能力            │
│  ├── DingTalkMessageService（复用）→ 新增分析报告通知      │
│  └── AgentLoop（复用）→ 新增无会话静默执行模式             │
└──────────────────────────────────────────────────────────┘
```

#### 3.14.2 定时任务模型

```java
public class ScheduledTask {
    private String taskId;
    private String taskName;
    private String threadPoolId;        // 目标线程池
    private String instanceId;          // 目标实例
    private String cronExpression;      // Cron 表达式
    private boolean enabled;
    private TaskAction action;          // 执行策略
    private boolean notifyOnComplete;
    private boolean autoAdjust;
    private TaskStatus status;
    private LocalDateTime lastExecTime;
    private LocalDateTime nextExecTime;
    private String source;              // "USER" / "AI"
}

public enum TaskAction {
    LOG_ONLY,           // 仅采集日志+分析
    NOTIFY_ONLY,        // 采集+分析+发通知
    AUTO_ADJUST         // 采集+分析+自动调整+通知
}
```

#### 3.14.3 定时任务执行流程

```
时间到达 → Cron 表达式匹配
  │
  ┌▼──────┐
  │ 调度器  │ TaskScheduler
  └───┬───┘
      │
  ┌───▼──────────────┐
  │ 收集运行日志       │ ThreadPoolLogStore.query()
  │ 收集当前配置       │ getPoolConfig()
  └───┬──────────────┘
      │
  ┌───▼──────────────┐
  │ AI 静默分析       │ AgentLoop.executeSilent()
  │ 输入: 运行日志 + 配置 + 业务场景
  │ 输出: AgentReport（结构化分析报告）
  └───┬──────────────┘
      │
  ┌───▼──────────────┐
  │ 根据配置执行操作    │
  │ LOG_ONLY          │ → 仅保存分析报告
  │ NOTIFY_ONLY       │ → 保存报告 + 发钉钉通知
  │ AUTO_ADJUST       │ → Agent 调整参数 + 通知
  └───┬──────────────┘
      │
  ┌───▼──────────────┐
  │ 记录执行结果       │ TaskExecutionLog
  │ 更新下次执行时间    │
  └──────────────────┘
```

#### 3.14.4 AI 工具定义

向 AI 暴露创建定时任务的工具：

```json
{
  "name": "create_scheduled_task",
  "description": "创建一个定时任务，定期分析指定线程池的运行情况并在必要时调整参数",
  "parameters": {
    "type": "object",
    "properties": {
      "task_name": { "type": "string" },
      "thread_pool_id": { "type": "string" },
      "cron_expression": { "type": "string" },
      "action": { "type": "string", "enum": ["LOG_ONLY", "NOTIFY_ONLY", "AUTO_ADJUST"] }
    },
    "required": ["task_name", "thread_pool_id", "cron_expression", "action"]
  }
}
```

#### 3.14.5 实现状态

- `ScheduledTask.java` — 任务模型 ✅
- `TaskExecutionLog.java` — 执行日志模型 ✅
- `ScheduledTaskService.java` — CRUD + JSON 文件持久化 ✅
- `TaskScheduler.java` — Spring TaskScheduler + CronTrigger 调度引擎 ✅
- `TaskExecutionService.java` — 执行全流程编排 ✅
- `AgentLoop.executeSilent()` — 静默分析模式 ✅
- `CreateScheduledTaskTool.java` — AI 工具注册 ✅
- `ScheduledTaskController.java` — REST 接口（8 个端点）✅

---

## 四、改造优先级

按实现价值和难度排序，建议分三个阶段推进：

### P0 — 基础能力补齐（核心改造）

| 改造项 | 说明 | 状态 |
|---|---|---|
| **原生 Function Calling** | 替换文本解析，使用 OpenAI tools 参数，消除工具调用的不可靠性 | 🔴 待实现 |
| **业务场景声明（businessType）** | ThreadPoolExecutorProperties 新增 BusinessType（含 CUSTOM 自定义描述），用于 AI 模块感知线程池业务属性 | ✅ 已实现 |
| **Tool Search（工具按需加载）** | 意图驱动的工具筛选，按分类注入而非全量注入，减少 70%~85% 工具定义 Token（参考 Anthropic MCP 实践） | 🔴 待实现 |
| **上下文管理** | 实现 ContextManager + 五阶段压缩（含程序化工具结果处理），解决 Token 超限问题 | ✅ 已实现 |
| **AgentLoop 重构** | 多阶段循环 + 不可变参数/可变状态分离 + 静默执行模式 | ✅ 静默模式已实现，多阶段循环待完善 |
| **错误处理与重试** | 级联恢复策略，提升健壮性 | 🔴 待实现 |
| **System Prompt 模板化** | 从硬编码改为模板 + 动态注入（工具列表由 Tool Search 提供） | ✅ 已实现 |
| **线程池运行日志** | ThreadPoolMonitor 增强，JSON Lines 文件日志存储，支持三级聚合查询 | ✅ 已实现 |
| **定时任务调度引擎** | TaskScheduler + TaskExecutionService + Silent AgentLoop | ✅ 已实现 |
| **AI 创建任务工具** | 注册 create_scheduled_task 工具到 ToolRegistry | ✅ 已实现 |

### P1 — 智能化增强

| 改造项 | 说明 | 状态 |
|---|---|---|
| **记忆管理** | 短期记忆（会话内）+ 长期记忆（结构化 Markdown 文件，grep 式检索）| 🔴 待实现（基础框架已就绪） |
| **知识库** | 12 篇内置业界最佳实践文档，结构化 Markdown 存储，加权评分检索 | ✅ 已实现 |
| **意图识别** | 规则 + LLM 两阶段识别，路由到不同处理流程 | ✅ 已实现 |
| **权限系统重构** | Deny-First 多层规则模型 | 🔴 待实现 |
| **流式响应** | SSE 推送，提升用户体验 | 🔴 待实现 |
| **REST 接口** | 定时任务 CRUD + 日志查询接口 | ✅ 已实现 |

### P2 — 进阶能力

| 改造项 | 说明 | 状态 |
|---|---|---|
| **工具并发执行** | 只读工具并行、写入工具串行 | 🔴 待实现 |
| **会话持久化** | 会话数据落盘或入库 | 🔴 待实现 |
| **多 LLM 适配** | 支持国产模型（通义千问、智谱等）Adapter | 🔴 待实现 |
| **衰减检测** | 连续低效迭代自动终止 | 🔴 待实现 |
| **代码编排模式** | 参考 Cloudflare 实践：只暴露 search + execute 两个元工具 | 🔴 待实现 |
| **定时任务 UI 页面** | 前端任务配置/执行历史页面 | 🔴 待实现（在 dashboard 前端项目） |
| **灰度发布策略** | 自动调整前先在小流量验证 | 🔴 待实现 |
| **多实例管理** | 定时任务支持跨实例调度 | 🔴 待实现 |

---

## 五、数据流全景

改造完成后，一次完整的用户交互流程：

```
用户: "producer 线程池队列快满了，帮我优化一下"
                           │
                    ┌──────▼──────┐
                    │ API Layer   │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ Orchestrator│
                    │  1. 加载会话 │
                    │  2. 意图识别 │ → Intent.OPTIMIZE
                    │  3. Tool Search │ → 筛选出 query/adjust/diagnose 类工具
                    │  4. 检索记忆 │ → "producer 是 IO 密集型，上次调优效果好"
                    │  5. 检索知识 │ → "IO 密集型线程池建议：core = CPU*2, max = CPU*4"
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ AgentLoop   │
                    │             │
                    │ Turn 1:     │
                    │  Pre: 注入记忆+知识到 prompt
                    │  LLM: "我需要先查看当前状态"
                    │  Tool: query_thread_pool(producer) → 返回指标
                    │  Post: 记录审计
                    │             │
                    │ Turn 2:     │
                    │  Pre: 上下文检查 OK
                    │  LLM: "队列使用率 92%，建议调整参数"
                    │  Tool: adjust_thread_pool(producer, {max: 20, queue: 2048})
                    │  Permission: 风险评估 → MEDIUM → 需审批
                    │  → 返回 approval_required
                    │             │
                    │ (用户审批通过) │
                    │             │
                    │ Turn 3:     │
                    │  执行调整 → 成功
                    │  Post: 写入长期记忆 "producer 在高峰期调整 max=20 效果良好"
                    │  LLM: 生成最终总结
                    │             │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ Response    │
                    │ "已将 producer 线程池最大线程从 10 调整为 20，
                    │  队列容量从 1024 调整为 2048。建议观察 15 分钟。" │
                    └─────────────┘
```

---

## 六、配置项设计

```yaml
anticipa:
  agent:
    enabled: true
    # LLM 配置
    llm:
      provider: openai           # openai / zhipu / qwen / custom
      base-url: https://api.openai.com
      api-key: ${AGENT_API_KEY}
      model: gpt-4
      temperature: 0.3
      max-tokens: 4096
      timeout-seconds: 60
    # Agent Loop 配置
    loop:
      max-turns: 15              # 最大轮次
      diminishing-threshold: 3   # 连续低效轮次阈值
    # 上下文管理
    context:
      max-token-budget: 128000   # Token 上限
      compact-threshold: 13000   # 触发压缩的剩余 Token 阈值
      tool-result-max-tokens: 2000 # 单个工具结果最大 Token
      programmatic-result-processing: true  # 启用程序化工具结果处理（内部聚合后再返回 LLM）
    # Tool Search 配置
    tool-search:
      enabled: true              # 启用按需加载（false 则退化为全量注入）
      max-tools-per-request: 8   # 单次请求最多注入的工具数量
      fallback-to-all: true      # Tool Search 未命中时是否回退到全量注入
    # 数据存储（知识库 + 长期记忆）
    data-store:
      root-path: ./data          # 数据根目录
      knowledge-path: ./data/knowledge  # 知识库文件目录
      memory-path: ./data/memory        # 长期记忆文件目录
      auto-init-builtin-knowledge: true # 首次运行时自动释放内置知识库文档
    # 知识库检索
    knowledge:
      enabled: true
      top-k: 3                   # 每次检索返回最多 3 条
      memory-top-k: 2            # 长期记忆返回最多 2 条
    # 权限
    permissions:
      deny: []
      ask: []
      allow: []
    # 重试
    retry:
      max-retries: 3
      backoff-multiplier: 2.0
      initial-delay-ms: 1000

# 线程池运行日志
anticipa:
  log:
    enabled: false                     # 运行日志开关
    record-interval-seconds: 10        # 记录频率
    retention-days: 7                  # 日志保留天数
    compress: true
    store-path: data/logs
    query:
      max-window-minutes: 60
      default-window-minutes: 30

# 定时任务持久化路径（在 agent 配置中）
anticipa:
  agent:
    store-path: data/agent            # 定时任务 JSON 持久化路径
```

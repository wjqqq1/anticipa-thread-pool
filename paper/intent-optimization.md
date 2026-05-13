# 基于大语言模型的智能体意图路由优化：从规则匹配到自主决策

## 摘要

在基于大语言模型（LLM）的智能运维 Agent 系统中，意图识别常被用作请求路由和工具过滤的前置环节。然而，基于关键词匹配的意图识别机制存在准确性不足、多意图冲突、维护成本高等问题。本文以 Anticipa 动态线程池治理平台为研究对象，分析了一种"移除显式意图识别、统一走 Agent 循环"的优化方案。该方案将意图决策权从规则引擎交还给 LLM，通过 Function Calling 机制让模型自主判断是否需要调用工具及调用哪些工具，同时配合"严禁瞎编"规则约束模型在无工具可用时的行为。实验分析表明，在工具规模有限（12个）的场景下，该方案在准确性、系统复杂度和可维护性上均优于关键词匹配和 LLM 二次意图识别两种替代方案。

---

## 1 引言

### 1.1 研究背景

随着大语言模型技术的发展，基于 Agent 架构的智能运维系统逐渐成为工业界的主流实践。典型的 Agent 系统采用"意图识别→工具检索→LLM 推理→工具执行"的流水线模式，其中意图识别环节负责判断用户请求的语义类别，以此决定后续处理路径和工具加载策略。

Anticipa 是一个面向 Java 应用的动态线程池自适应治理平台，其 AI 智能体模块提供了线程池查询监控、参数动态调整、问题诊断、优化建议、定时巡检等核心能力。系统采用 6 阶段 Agent Loop 架构，内置 12 个工具函数，支持 SSE 流式交互和 Deny-First 权限审批模型。

### 1.2 问题描述

Anticipa 系统的早期版本采用基于关键词匹配的意图识别机制，在用户请求进入 Agent 循环前，通过字符串包含检测将请求分类为 QUERY、ADJUST、DIAGNOSE、OPTIMIZE、EXPLAIN、CHAT 等 7 种意图类别，并据此进行路由分流和工具过滤。该机制在实际运行中暴露出以下问题：

1. **语义覆盖不足**：自然语言表述多样化，关键词无法穷举所有语义等价表达。例如"帮我看下这个池子跑得怎么样"、"线程池扛不住了"等常见运维口语无法命中任何关键词，被错误归类为闲聊（CHAT），导致用户无法获得工具支持。

2. **多意图冲突**：关键词匹配采用短路优先策略，当用户消息同时包含多个意图类别的关键词时（如"查看一下拒绝率为什么这么高"同时命中查询和诊断关键词），只能返回第一个匹配的意图，丢失了复合语义。

3. **维护成本递增**：每新增一种意图或扩展关键词覆盖面，需手动调整关键词列表，且存在两套不一致的匹配逻辑（IntentRecognizer 与 AgentLoop 内联的 classifyByKeywords），增加了维护负担和出错概率。

4. **误判代价不对称**：将操作意图误判为闲聊会导致用户完全无法使用工具（功能级缺陷），而将闲聊误判为操作意图仅增加少量 Token 消耗（性能级影响），两者代价严重不对称。

### 1.3 研究贡献

本文的主要贡献如下：

- 系统分析了三种意图路由方案（关键词匹配、LLM 二次意图识别、移除意图识别）在准确性、性能、复杂度和可维护性四个维度的优劣。
- 提出并实施了一种"去意图化"的 Agent 架构优化方案，将意图决策权交还给 LLM，通过 Function Calling 和 Prompt 约束实现自主决策。
- 在 Anticipa 系统上完成了方案落地，涉及 7 个文件的修改和 3 个模块的删除，代码量净减少约 300 行。

---

## 2 相关工作

### 2.1 意图识别方法

传统意图识别方法主要包括基于规则的方法和基于机器学习的方法。基于规则的方法依赖关键词匹配、正则表达式或决策树，优点是延迟低、可解释性强，但泛化能力有限。基于机器学习的方法（如 SVM、BERT 意图分类器）具有更好的语义理解能力，但需要标注数据集和模型训练。

在 LLM 时代，意图识别可以通过 Prompt Engineering 让 LLM 直接进行分类判断。然而，在 Agent 系统中引入额外的 LLM 调用进行意图识别，会带来额外的延迟和 Token 消耗，形成"用 LLM 解决 LLM 自身就能解决的问题"的悖论。

### 2.2 Agent 架构实践

Anthropic 在《Building agents that reach production systems with MCP》实践指南中建议，当工具数量达到几十甚至上百个时，应采用按需加载策略减少 System Prompt 中的工具定义 Token 消耗。但当工具规模有限时，全量注入的开销完全可以接受，此时意图过滤的收益不足以覆盖其引入的复杂度和准确性风险。

OpenAI 的 Function Calling 机制本身就是一种隐式的意图识别——模型根据用户输入和可用工具定义，自主决定是否调用工具及调用哪个工具。这种端到端的方式避免了中间层的准确性损失。

---

## 3 系统架构

### 3.1 Anticipa Agent 整体架构

Anticipa Agent 采用 6 阶段循环架构，参考 Claude Code 的设计范式：

| 阶段 | 名称 | 职责 |
|------|------|------|
| Stage 1 | Pre-Request | 上下文压缩、记忆注入、知识增强 |
| Stage 2 | API Call | LLM 调用 + 原生 Function Calling + 重试 |
| Stage 3 | Response Processing | 判断 end_turn / tool_use |
| Stage 4 | Tool Execution | 工具执行 + Deny-First 权限检查 + 并发控制 |
| Stage 5 | Post-Turn | 审计记录、记忆更新、衰减检测 |
| Stage 6 | Loop Control | 最大轮次、Token 预算、错误恢复 |

系统内置 12 个工具函数，按功能分为 4 个类别：

| 类别 | 工具 | 数量 |
|------|------|------|
| QUERY | query_thread_pool, list_thread_pools, list_namespaces, list_services, list_nacos_configs, list_instances | 6 |
| ADJUST | adjust_thread_pool, update_thread_pool_config | 2 |
| DIAGNOSE | query_history, check_logs_exists, analyze_trends | 3 |
| SYSTEM | create/list/delete/pause/resume/query_scheduled_task | 7（定时任务系列） |

### 3.2 优化前的意图识别架构

优化前，系统在 Agent 循环前设置了意图识别和路由分流环节：

```
用户消息 → 意图识别（关键词匹配） → 路由分流
    ├── CHAT/EXPLAIN → 直接回答路径（无工具，单次 LLM 调用）
    └── 其他意图 → 标准 Agent 路径（工具过滤 + 多轮循环）
```

意图识别由两套独立的实现组成：
- `IntentRecognizer`：独立的意图识别器组件，通过 Spring Bean 注入
- `AgentLoop.classifyByKeywords()`：AgentLoop 内联的关键词匹配方法

两套实现的关键词列表和匹配优先级存在差异，增加了维护难度和一致性风险。

此外，当关键词匹配返回 CHAT 时，系统会检查对话历史判断是否为 follow-up（如用户在多轮对话中补充参数），尝试从历史消息中继承意图。该机制依赖对 assistant 回复的固定措辞检测（"请提供"、"请指定"等 10 个关键词），覆盖面有限。

### 3.3 优化后的统一 Agent 架构

优化后，系统移除了意图识别和路由分流环节，所有请求统一走标准 Agent 循环：

```
用户消息 → 工具检索（全量+相关性排序） → 统一 Agent 循环
    └── LLM 自主决定：调用工具 → 工具执行 → 继续循环
                    或：直接回答 → end_turn
```

关键设计决策：

1. **全量工具注入**：12 个工具全量注入 System Prompt，LLM 通过 Function Calling 自主选择。
2. **相关性排序**：`ToolSearchService` 按关键词相关性对工具排序，最相关的工具排在前面，帮助 LLM 更快定位。
3. **Prompt 约束**：在 System Prompt 中增加"严禁瞎编"规则，约束 LLM 在无工具数据支撑时不编造具体数值，闲聊时不强行调用工具。

---

## 4 方案评估

### 4.1 三种方案对比

本文从准确性、性能、复杂度和可维护性四个维度，对三种方案进行系统评估。

#### 方案 A：关键词匹配 + 历史推断（原方案）

**准确性：中等偏下**

关键词匹配对标准运维指令（"查看线程池状态"）识别较好，但对自然语言多样化表达覆盖不足。顺序敏感的短路匹配机制导致多意图冲突时只能返回第一个命中意图。Follow-up 推断依赖固定措辞检测，覆盖面有限。

典型误判案例：
- "帮我看下这个池子跑得怎么样" → CHAT（无关键词命中）
- "查看一下拒绝率为什么这么高" → QUERY（"查看"优先命中，丢失 DIAGNOSE 语义）
- "能不能把核心线程数提一提" → CHAT（"提"不在关键词列表中）

**性能：极好**

纯字符串 `contains` 操作，耗时 < 1ms，零外部调用。

**复杂度：中等**

逻辑简单直白，但存在两套重复且不一致的实现（IntentRecognizer 与 classifyByKeywords）。

**维护成本：高**

每新增意图需手动调整关键词列表；两套实现需同步维护；关键词冲突需人工仲裁；无法量化准确率。

#### 方案 B：LLM 二次意图识别

**准确性：高**

LLM 天然擅长语义理解，能处理自然语言多样性、多意图冲突和 Follow-up 场景。

**性能：较差**

每次用户请求增加一次额外的 LLM 调用，额外延迟 500ms~2s，额外 Token 消耗约 200~500 tokens。对于直接回答路径（原 CHAT/EXPLAIN），延迟翻倍。

关键矛盾：意图过滤的目的是减少工具定义的 Token 消耗，但 LLM 意图识别本身消耗的 Token 和时间可能抵消甚至超过其节省量。

Token 开销分析：
```
全量工具定义约 3000 tokens
意图过滤后平均注入约 1000 tokens
节省约 2000 tokens/请求

LLM 意图识别额外消耗约 300 tokens/请求 + 1~2s 延迟
净 Token 节省 = 2000 - 300 = 1700（仅标准路径有效）
直接回答路径反而增加 300 tokens
```

**复杂度：中等**

需设计 Intent 分类 Prompt、输出格式约束、异常处理和降级回退策略。

**维护成本：中等**

新增意图只需修改枚举和 Prompt 描述，但需维护 Prompt 模板；LLM 版本更迭可能导致行为漂移。

#### 方案 C：移除意图识别，统一 Agent 循环（采用方案）

**准确性：高**

不存在"该走工具路径却被判为闲聊"的误判问题。LLM 通过 Function Calling 自主选择工具，语义理解能力远超关键词匹配。闲聊场景下 LLM 首轮即返回 end_turn，不会错误调用工具。

**性能：中等**

| 场景 | 方案 A | 方案 C |
|------|--------|--------|
| 闲聊/解释 | 1 次 LLM 调用（直接回答） | 1 次 LLM 调用（首轮 end_turn） |
| 查询/调整 | 1+ 次 LLM 调用 + 过滤工具 | 1+ 次 LLM 调用 + 全量工具 |

闲聊场景下两者延迟基本一致（均为单次 LLM 调用），标准路径下全量工具注入额外消耗约 2000 tokens。

**复杂度：最低**

删除意图识别相关代码，删除直接回答路径，统一处理流程，代码量净减少约 300 行。

**维护成本：最低**

无需维护关键词列表、意图到工具分类的映射；新增工具只需注册，无需考虑意图归类。

### 4.2 综合评估

| 维度 | 方案 A（关键词） | 方案 B（LLM 意图） | 方案 C（去意图化） |
|------|:---:|:---:|:---:|
| 准确性 | ★★☆☆☆ | ★★★★☆ | ★★★★☆ |
| 延迟 | ★★★★★ | ★★☆☆☆ | ★★★★☆ |
| Token 开销 | ★★★★★ | ★★★☆☆ | ★★★★☆ |
| 代码复杂度 | ★★★☆☆ | ★★☆☆☆ | ★★★★★ |
| 维护成本 | ★★☆☆☆ | ★★★☆☆ | ★★★★★ |
| 扩展性 | ★★☆☆☆ | ★★★☆☆ | ★★★★★ |
| 闲聊安全性 | ★★★★★ | ★★★☆☆ | ★★★★☆ |

方案 C 在准确性、复杂度和可维护性上均占优，仅在 Token 开销上略逊于方案 A（额外约 2000 tokens/请求），远优于方案 B（额外 LLM 调用的开销）。在当前工具规模（12 个）下，这一 Token 差额完全可以接受。

### 4.3 方案适用边界

方案 C 的适用前提是工具规模有限。当工具数量超过 20~30 个时，全量注入的 Token 开销将显著增加，此时应考虑方案 B 的变体——使用轻量级 Embedding 相似度检索替代 LLM 意图识别，既保留按需加载的收益，又避免额外 LLM 调用的延迟和成本。

---

## 5 实现细节

### 5.1 代码变更

本次优化共涉及 7 个文件的修改和 3 个文件的删除：

**删除的模块：**

| 文件 | 说明 |
|------|------|
| `IntentRecognizer.java` | 关键词意图识别器 |
| `IntentRouter.java` | 意图路由器 |
| `Intent.java` | 意图枚举定义 |

**修改的模块：**

| 文件 | 变更内容 |
|------|---------|
| `AgentLoop.java` | 移除意图识别、直接回答路径、Follow-up 推断等 5 个方法；移除 AgentState.intent 字段；统一走标准 Agent 循环 |
| `ToolSearchService.java` | 移除 Intent 参数和 intentToCategories 映射；search() 改为全量工具+相关性排序 |
| `SystemPromptBuilder.java` | 移除意图过滤，始终注入工具列表；PromptContext 移除 intent 字段；新增"严禁瞎编"安全规则 |
| `AgentEventListener.java` | 移除 onIntentRecognized 回调方法 |
| `PipelineEvent.java` | 移除 INTENT 事件常量 |
| `AgentAutoConfiguration.java` | 移除 IntentRecognizer 和 IntentRouter 的 Bean 注册 |
| `AIChatController.java` (×2) | 移除 Intent 导入和 onIntentRecognized SSE 回调 |

### 5.2 关键设计：严禁瞎编规则

移除意图识别后，所有请求（包括闲聊）都会注入工具定义。为确保 LLM 不会在闲聊场景下错误调用工具或在无数据支撑时编造数值，在 System Prompt 的安全规则部分新增了"严禁瞎编"约束：

```
## 严禁瞎编

- 严禁编造任何线程池的运行状态、参数数值、实例数量等具体数据
- 如果用户询问具体数据，必须通过工具查询获取准确数据，不能凭空给出
- 对于与线程池无关的闲聊，友好简洁回答即可，不要调用工具
- 你只能提供概念解释、通用建议和框架介绍，不能凭空给出具体的监控数值
```

该规则源自优化前 `directAnswer` 路径中的同类约束，但在新架构下具有更广泛的适用性——它同时约束了"闲聊不调用工具"和"有工具时必须查数据不编造"两种场景。

### 5.3 工具相关性排序

虽然全量注入工具，但通过 `ToolSearchService.rankByRelevance()` 对工具按关键词相关性排序，将与用户输入最相关的工具排在前面：

```java
private List<ToolDefinition> rankByRelevance(List<ToolDefinition> tools, String userMessage) {
    return tools.stream()
            .sorted(Comparator.comparingDouble((ToolDefinition t) -> {
                double score = 0;
                if (t.getName().toLowerCase().contains(msgLower)) score += 3;
                if (t.getDescription().toLowerCase().contains(msgLower)) score += 2;
                if (t.getParameterSchema() != null) {
                    String schemaStr = t.getParameterSchema().toString().toLowerCase();
                    if (schemaStr.contains(msgLower)) score += 1;
                }
                return -score; // 降序
            }))
            .collect(Collectors.toList());
}
```

排序权重设计：工具名称匹配（3分）> 描述匹配（2分）> 参数 Schema 匹配（1分），反映了对不同信息源的可信度差异。

---

## 6 实验与分析

### 6.1 准确性对比

选取 30 条典型运维对话作为测试集，对比优化前后的意图路由准确性：

| 输入示例 | 优化前（关键词） | 优化后（LLM 自主） |
|---------|:---:|:---:|
| "查看线程池状态" | ✅ QUERY | ✅ 调用 query_thread_pool |
| "帮我看下这个池子跑得怎么样" | ❌ CHAT | ✅ 调用 query_thread_pool |
| "线程池扛不住了" | ❌ CHAT | ✅ 调用 query_thread_pool + diagnose |
| "查看一下拒绝率为什么这么高" | ⚠️ QUERY（丢失 DIAGNOSE） | ✅ 调用 query + diagnose 工具 |
| "能不能把核心线程数提一提" | ❌ CHAT | ✅ 调用 adjust_thread_pool |
| "什么是拒绝策略" | ✅ EXPLAIN | ✅ 直接回答（不调用工具） |
| "你好" | ✅ CHAT | ✅ 直接回答（不调用工具） |

优化后，LLM 通过 Function Calling 自主决策，能够正确处理关键词匹配无法覆盖的自然语言表达，同时保持对闲聊和概念解释类请求的正确处理（不调用工具，直接回答）。

### 6.2 性能影响分析

**闲聊场景**：优化前走直接回答路径（单次 LLM 调用），优化后走标准 Agent 路径的首轮 end_turn，延迟基本一致，Token 消耗增加约 2000 tokens（工具定义部分）。

**操作场景**：优化前后均走标准 Agent 路径，延迟一致。Token 消耗取决于工具注入量：优化前按意图过滤注入约 4 个工具（~1000 tokens），优化后全量注入 12 个工具（~3000 tokens），额外消耗约 2000 tokens。

**Token 增量评估**：按 GPT-4 级别定价（$0.03/1K input tokens），每请求额外成本约 $0.06。对于企业内部运维场景，这一增量完全可接受。

### 6.3 代码复杂度分析

| 指标 | 优化前 | 优化后 | 变化 |
|------|--------|--------|------|
| 意图识别相关代码行数 | ~150 行 | 0 行 | -150 |
| 直接回答路径代码行数 | ~120 行 | 0 行 | -120 |
| ToolSearchService 代码行数 | ~142 行 | ~65 行 | -77 |
| AgentLoop 核心方法数 | 13 个 | 6 个 | -7 |
| 配置 Bean 数 | 2 个（IntentRecognizer + IntentRouter） | 0 个 | -2 |
| 总计净减少 | — | — | **~300 行** |

---

## 7 结论与展望

本文针对基于关键词匹配的意图识别机制在智能运维 Agent 系统中的局限性，提出并实施了一种"去意图化"的架构优化方案。该方案的核心思想是将意图决策权从规则引擎交还给 LLM，通过 Function Calling 机制和 Prompt 约束实现自主决策。

在 Anticipa 动态线程池治理平台上的实践表明：

1. **准确性提升**：消除了关键词匹配的误判问题，LLM 能正确处理自然语言多样化表达。
2. **架构简化**：移除了意图识别、路由分流、直接回答三条独立路径，代码量净减少约 300 行。
3. **维护成本降低**：无需维护关键词列表和意图到工具的映射关系，新增工具只需注册即可。
4. **性能代价可控**：闲聊场景延迟不变，操作场景 Token 消耗增加约 2000 tokens/请求，在当前工具规模下完全可接受。

**局限性**：本方案的前提是工具规模有限（<30 个）。当工具数量显著增长时，全量注入的 Token 开销将成为瓶颈，此时需要引入基于 Embedding 的工具检索机制作为替代方案。

**未来工作**：
- 基于工具 Embedding 的语义检索，在保留 LLM 自主决策的同时实现工具的按需加载。
- 引入工具调用成功率反馈机制，动态调整工具排序权重。
- 探索工具组合模式（Tool Composition）的自动发现与推荐。

---

## 参考文献

[1] Anthropic. Building agents that reach production systems with MCP. 2024.

[2] OpenAI. Function Calling and Parallel Function Calling. 2024.

[3] Wang L, et al. A Survey on Large Language Model based Autonomous Agents. arXiv:2308.11432, 2023.

[4] Yao S, et al. ReAct: Synergizing Reasoning and Acting in Language Models. ICLR, 2023.

[5] Shinn N, et al. Reflexion: Language Agents with Verbal Reinforcement Learning. NeurIPS, 2023.

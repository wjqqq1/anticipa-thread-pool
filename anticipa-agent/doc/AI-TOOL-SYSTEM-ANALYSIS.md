# Anticipa Agent 工具体系分析

> 对当前 AI 模块的工具系统进行全面盘点：已实现工具的详细能力、缺失工具的需求分析、基础设施层已就绪但未暴露的能力，以及各核心场景的工具完备度评估。

---

## 一、工具系统架构总览

### 1.1 核心组件

```
┌───────────────────────────────────────────────────────────┐
│                    AgentLoop（六阶段循环）                    │
│  Pre-Request → API Call → Response → Tool Exec → Post → Loop │
├───────────────────────────────────────────────────────────┤
│                  ToolSearchService（按需检索）                │
│         按 Intent 映射 ToolCategory，减少 Token 消耗          │
├───────────────────────────────────────────────────────────┤
│                    ToolRegistry（注册表）                    │
│            Map<String, ToolDefinition> 全量注册              │
├───────────────────────────────────────────────────────────┤
│              PermissionManager + RiskEvaluator              │
│         Deny-First 权限模型 · LOW/MEDIUM/HIGH 风险评级       │
├───────────────────────────────────────────────────────────┤
│                  ApprovalService（审批服务）                  │
│            内存审批队列 · 支持 Accept/Reject/Modify           │
├───────────────────────────────────────────────────────────┤
│                    AuditStore（审计记录）                    │
│            CopyOnWriteArrayList · 按 sessionId 查询          │
└───────────────────────────────────────────────────────────┘
```

### 1.2 工具分类体系

```java
public enum ToolCategory {
    QUERY,    // 查询类：查状态、查指标、查配置
    ADJUST,   // 调整类：调参数、批量调、回滚
    DIAGNOSE, // 诊断类：查历史、查告警、分析趋势
    SYSTEM    // 系统类：实例信息、健康检查
}
```

### 1.3 意图-分类映射关系

| 意图 (Intent) | 映射的 ToolCategory | 说明 |
|---|---|---|
| `QUERY` | QUERY | 纯查询，只需查询工具 |
| `ADJUST` | QUERY + ADJUST | 调整前需先查询当前状态 |
| `DIAGNOSE` | QUERY + DIAGNOSE | 诊断需查询 + 历史数据分析 |
| `OPTIMIZE` | QUERY + ADJUST + DIAGNOSE | 优化需全量工具支持 |
| `BATCH_OPERATE` | QUERY + ADJUST | 批量操作需查询 + 调整 |
| `SCHEDULE_TASK` | QUERY + SYSTEM | 定时任务需查询 + 系统工具 |
| `CHAT` / `EXPLAIN` | 空 | 不注入工具，直接回答 |

### 1.4 工具定义模型

```java
public class ToolDefinition {
    private String name;                               // 工具名（全局唯一）
    private String description;                        // 描述（注入 System Prompt）
    private ToolCategory category;                     // 分类（用于 ToolSearch 筛选）
    private boolean modification;                      // 是否修改操作
    private boolean needsApproval;                     // 是否需要审批
    private boolean concurrencySafe;                   // 是否可并发执行
    private Map<String, Object> parameterSchema;       // JSON Schema 参数定义
    private Function<Map<String, Object>, ToolResult> executor;  // 执行函数
}
```

### 1.5 工具执行结果

```java
public class ToolResult {
    private boolean success;            // 执行是否成功
    private String summary;             // 人类可读的摘要（注入回 LLM 上下文）
    private Map<String, Object> data;   // 结构化数据（可由前端消费）
    private String rawJson;             // 原始 JSON（可选）
}
```

---

## 二、已实现工具详细分析

### 2.1 `query_thread_pool` — 查询线程池运行时状态

| 属性 | 值 |
|---|---|
| **分类** | QUERY |
| **修改操作** | 否 |
| **需要审批** | 否 |
| **并发安全** | 是 |
| **源码位置** | `anticipa-agent/.../tool/QueryThreadPoolTool.java` |

**参数定义：**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `thread_pool_id` | string | 是 | 线程池 ID，例如 `order-service` |
| `namespace` | string | 否 | Nacos 命名空间（远程模式必填） |
| `service_name` | string | 否 | Nacos 服务名（远程模式必填） |
| `instance_id` | string | 否 | 实例 ID（为空则返回该服务下所有实例的指标） |

**返回指标：**

```
corePoolSize · maximumPoolSize · activeCount · poolSize
queueSize · queueCapacity · queueUsagePercent
completedTaskCount
```

**底层依赖：** `ThreadPoolQueryService.queryPoolMetrics()`

**局限性：**
- 只能查询单个线程池，无法一次获取所有线程池概览
- 没有时间维度的指标，只能看当前瞬时值
- 未返回拒绝策略、keepAliveTime 等配置信息

---

### 2.2 `adjust_thread_pool` — 调整线程池参数

| 属性 | 值 |
|---|---|
| **分类** | ADJUST |
| **修改操作** | 是 |
| **需要审批** | 是 |
| **并发安全** | 否 |
| **源码位置** | `anticipa-agent/.../tool/AdjustThreadPoolTool.java` |

**参数定义：**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `thread_pool_id` | string | 是 | 线程池 ID |
| `namespace` | string | 否 | Nacos 命名空间 |
| `service_name` | string | 否 | Nacos 服务名 |
| `instance_id` | string | 否 | 实例 ID（为空则调整所有实例） |
| `core_pool_size` | integer | 否 | 核心线程数 |
| `maximum_pool_size` | integer | 否 | 最大线程数 |
| `queue_capacity` | integer | 否 | 队列容量 |
| `keep_alive_seconds` | integer | 否 | 线程存活时间（秒） |

**内置安全校验：**

```
corePoolSize     > 0      （不允许设为 0）
maximumPoolSize  > 0      （不允许设为 0）
maximumPoolSize  <= 500   （硬上限）
queueCapacity    > 0      （不允许设为 0）
```

**执行流程：**

```
参数校验 → 安全校验 → ThreadPoolQueryService.adjustPool()
                                        ↓
                               返回 before/after/changes
```

**底层依赖：** `ThreadPoolQueryService.adjustPool()`

**审批流程：**

工具标记 `needsApproval=true` → PermissionManager 评估 → 返回 `ASK` → ApprovalService 创建审批请求 → 等待用户确认（支持 Accept/Reject/Modify）

**局限性：**
- 只能单实例调整，不支持批量操作
- 没有回滚机制（调整后无法一键恢复到调整前的值）
- 没有模拟模式（无法预览调整效果而不实际执行）
- 未记录调整前后对比快照（AuditStore 只记录了 summary）

---

### 2.3 `list_instances` — 列举所有服务实例

| 属性 | 值 |
|---|---|
| **分类** | QUERY |
| **修改操作** | 否 |
| **需要审批** | 否 |
| **并发安全** | 是 |
| **源码位置** | `anticipa-agent/.../discovery/ListInstancesTool.java` |

**参数定义：**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `service_name` | string | 否 | 服务名（为空则返回所有实例） |

**返回数据：**

```
instanceId · appName · host · port · status · threadPoolIds[]
```

**底层依赖：** `InstanceDiscoveryService.discoverAllInstances()`

**局限性：**
- 未返回实例的健康状态详情（只返回 ONLINE/OFFLINE）
- 未返回实例级别的负载概览（CPU、内存等）
- `service_name` 参数传入后并未在 discoverAllInstances() 中过滤，形同虚设

---

### 2.4 `create_scheduled_task` — 创建定时分析任务

| 属性 | 值 |
|---|---|
| **分类** | 未设置（`ToolCategory` 为 null） |
| **修改操作** | 否 |
| **需要审批** | 否 |
| **并发安全** | 否 |
| **源码位置** | `anticipa-agent/.../scheduled/CreateScheduledTaskTool.java` |

**参数定义：**

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `task_name` | string | 是 | 任务名称 |
| `thread_pool_id` | string | 是 | 目标线程池 ID |
| `cron_expression` | string | 是 | Cron 表达式，如 `0 0 14 * * ?` |
| `action` | string(枚举) | 是 | `LOG_ONLY` / `NOTIFY_ONLY` / `AUTO_ADJUST` |
| `instance_id` | string | 否 | 目标实例 ID |
| `auto_adjust` | boolean | 否 | 是否自动调整参数 |
| `notify_on_complete` | boolean | 否 | 执行完成后是否通知 |
| `description` | string | 否 | 任务描述 |

**执行策略说明：**

| action | 行为 |
|---|---|
| `LOG_ONLY` | 仅采集日志 + AI 分析，结果写入日志 |
| `NOTIFY_ONLY` | 采集 + 分析 + 发钉钉通知 |
| `AUTO_ADJUST` | 采集 + 分析 + 直接调整参数 + 发钉钉通知 |

**底层依赖：** `ScheduledTaskService.create()` + `TaskScheduler.startTask()`

**问题：**
- **未设置 ToolCategory**，导致 ToolSearchService 按意图筛选时可能遗漏
- **不需要审批但 action=AUTO_ADJUST 会自动修改参数**，存在安全风险
- 只能创建任务，不能查询/修改/删除/暂停已有任务
- 缺少任务执行历史查询能力

---

## 三、已定义但未实现的工具/能力

### 3.1 被权限系统引用但工具本体不存在

| 工具名 | 引用位置 | 引用内容 |
|---|---|---|
| `batch_adjust` | `PermissionManager.initBuiltinRules()` 第 49 行 | `new PermissionRule("batch_adjust", "ask", null, "批量调整操作始终需要审批")` |
| `batch_adjust` | `RiskEvaluator.evaluate()` 第 29 行 | `if ("batch_adjust".equals(toolName)) return RiskLevel.HIGH;` |

**分析：** 权限规则和风险评级已预留，说明 `batch_adjust` 曾在设计计划中，但工具本身从未实现。当 LLM 尝试调用 `batch_adjust` 时，AgentLoop 会返回"工具不存在"。

### 3.2 被 ToolCategory 注释描述但无工具实现

| 分类 | 注释描述 | 缺失的具体工具 |
|---|---|---|
| ADJUST | "调参数、批量调、**回滚**" | `rollback_thread_pool` — 配置回滚 |
| DIAGNOSE | "**查历史**、**查告警**、**分析趋势**" | `query_history`、`query_alerts`、`analyze_trends` |
| SYSTEM | "实例信息、**健康检查**" | `health_check` — 系统健康检查 |

### 3.3 被项目功能树定义但无工具实现

项目功能树中已规划但未实现的能力：

```
E2a2  批量调整      → 无 batch_adjust 工具
E2a3  调整模拟      → 无 simulate_adjust 工具
E2a4  配置回滚      → 无 rollback_thread_pool 工具
E2a5  调整历史      → 无 query_adjust_history 工具
```

---

## 四、基础设施层已就绪但未暴露为工具的能力

系统中已有丰富的底层服务，其方法可以直接或稍加包装后注册为 AI 工具：

### 4.1 ThreadPoolLogStore — 运行日志存储与查询

| 已有方法 | 位置 | 可暴露为工具 |
|---|---|---|
| `query(threadPoolId, startTime, endTime, aggregation)` | `ThreadPoolLogStore.java:125` | `query_history` — 查询历史运行指标 |
| `summary(threadPoolId, startTime, endTime)` | `ThreadPoolLogStore.java:165` | `analyze_trends` — 趋势分析与摘要统计 |
| `cleanExpired()` | `ThreadPoolLogStore.java:206` | — （内部维护，不需要暴露） |

**ThreadPoolLogStore.LogSummary 已有的统计指标：**

```
recordCount        记录条数
avgActiveCount     平均活跃线程数
maxActiveCount     峰值活跃线程数
avgPoolSize        平均线程数
maxPoolSize        峰值线程数
avgQueueUsagePercent  平均队列使用率(%)
maxQueueUsagePercent  峰值队列使用率(%)
totalRejectCount   总拒绝次数
```

**聚合策略：** `RAW`（原始） / `MINUTE`（分钟聚合） / `HOUR`（小时聚合）

**暴露方案：**

```java
// query_history 工具参数
{
  "thread_pool_id": "order-service",
  "start_time": "2026-04-28T00:00:00",
  "end_time": "2026-04-28T23:59:59",
  "aggregation": "HOUR"    // RAW | MINUTE | HOUR
}

// analyze_trends 工具参数
{
  "thread_pool_id": "order-service",
  "start_time": "2026-04-27T00:00:00",
  "end_time": "2026-04-28T00:00:00"
}
```

### 4.2 AuditStore — 审计记录

| 已有方法 | 位置 | 可暴露为工具 |
|---|---|---|
| `getHistory(sessionId, limit)` | `AuditStore.java:25` | `query_adjust_history` — 查询调整历史 |

**AuditRecord 已有字段：**

```
auditId · sessionId · timestamp · userId
toolName · params · riskLevel
requiresApproval · result · durationMs
```

**当前局限：** `getHistory()` 只按 sessionId 查询，缺少按 threadPoolId / 时间范围 / 工具名的查询维度。需要扩展后才能作为有价值的 AI 工具。

**暴露方案：**

```java
// query_adjust_history 工具参数
{
  "thread_pool_id": "order-service",   // 可选，按线程池过滤
  "session_id": "xxx",                 // 可选，按会话过滤
  "tool_name": "adjust_thread_pool",   // 可选，按工具名过滤
  "limit": 20                          // 返回条数上限
}
```

### 4.3 ThreadPoolAlarmChecker — 告警检查器

| 已有能力 | 位置 | 可暴露为工具 |
|---|---|---|
| 队列使用率告警 `checkQueueUsage()` | `ThreadPoolAlarmChecker.java:77` | `query_alerts` — 查询告警状态 |
| 线程活跃度告警 `checkActiveRate()` | `ThreadPoolAlarmChecker.java:100` | 同上 |
| 拒绝次数告警 `checkRejectCount()` | `ThreadPoolAlarmChecker.java:122` | 同上 |

**当前局限：** 告警检查器只有"检查+推送"逻辑，没有"查询历史告警"的能力。需要新增告警存储才能暴露为工具。

### 4.4 ThreadPoolQueryService.listPoolIds() — 线程池 ID 列表

| 已有方法 | 位置 | 可暴露为工具 |
|---|---|---|
| `listPoolIds()` | `ThreadPoolQueryService.java:49` | `list_thread_pools` — 列举所有线程池 |

**当前局限：** `QueryThreadPoolTool` 中只在找不到线程池时用 `listPoolIds()` 提示，但 AI 无法主动获取线程池列表来引导用户操作。

### 4.5 ScheduledTaskService — 定时任务管理

| 已有方法 | 位置 | 可暴露为工具 |
|---|---|---|
| `list()` | `ScheduledTaskService.java:59` | `list_scheduled_tasks` — 查询所有定时任务 |
| `get(taskId)` | `ScheduledTaskService.java:56` | — |
| `toggle(taskId)` | `ScheduledTaskService.java:75` | `toggle_scheduled_task` — 启停定时任务 |
| `delete(taskId)` | `ScheduledTaskService.java:69` | `delete_scheduled_task` — 删除定时任务 |
| `getExecutionLogs(taskId)` | `ScheduledTaskService.java:99` | `query_task_logs` — 查询任务执行历史 |
| `getRecentExecutionLogs(taskId, limit)` | `ScheduledTaskService.java:103` | 同上 |

### 4.6 TaskScheduler — 定时任务调度控制

| 已有方法 | 位置 | 可暴露为工具 |
|---|---|---|
| `pauseTask(taskId)` | `TaskScheduler.java:101` | `pause_scheduled_task` — 暂停定时任务 |
| `resumeTask(taskId)` | `TaskScheduler.java:113` | `resume_scheduled_task` — 恢复定时任务 |
| `executeNow(taskId)` | `TaskScheduler.java:124` | `execute_task_now` — 立即执行一次任务 |

### 4.7 RejectionAnalysisListener — 拒绝事件分析

| 已有能力 | 位置 | 可暴露为工具 |
|---|---|---|
| 拉取拒绝前后日志 + AI 分析 + 通知 | `RejectionAnalysisListener.java` | `query_rejection_log` — 查询拒绝事件分析记录 |

**当前局限：** 拒绝分析结果只在日志中输出和通过钉钉推送，没有持久化存储。AI 无法主动查询过去的拒绝分析记录。

### 4.8 MemoryManager — 记忆管理

| 已有方法 | 位置 | 可暴露为工具 |
|---|---|---|
| `recordAdjustment(...)` | `MemoryManager.java:86` | 内部自动调用，不需要暴露 |
| `recordDiagnosis(...)` | `MemoryManager.java:103` | 内部自动调用，不需要暴露 |
| `searchMemoryText(...)` | `MemoryManager.java:120` | 已在 AgentLoop 中自动注入，不需要单独暴露 |

---

## 五、核心场景工具完备度评估

### 5.1 场景：AI 驱动线程池修改

**典型对话：** "帮我把 order-service 的核心线程数调到 20"

| 步骤 | 所需工具 | 当前状态 |
|---|---|---|
| 1. 发现目标线程池 | `list_thread_pools` / `list_instances` | `list_instances` 已实现（部分覆盖） |
| 2. 查询当前配置 | `query_thread_pool` | 已实现 |
| 3. 评估调整风险 | PermissionManager + RiskEvaluator | 已实现 |
| 4. 执行调整 | `adjust_thread_pool` | 已实现 |
| 5. 审批确认 | ApprovalService | 已实现 |
| 6. 验证调整效果 | `query_thread_pool`（再次调用） | 已实现 |
| 7. 回滚（如需） | `rollback_thread_pool` | **未实现** |

**完备度：85%** — 核心流程已通，缺少回滚和调整前快照。

---

### 5.2 场景：线程池优化建议

**典型对话：** "帮我优化一下 order-service 线程池的配置"

| 步骤 | 所需工具 | 当前状态 |
|---|---|---|
| 1. 查询当前配置 | `query_thread_pool` | 已实现 |
| 2. 查询历史运行趋势 | `analyze_trends` | **未实现** |
| 3. 查询历史运行数据 | `query_history` | **未实现** |
| 4. 查询告警记录 | `query_alerts` | **未实现** |
| 5. 查询历史调整效果 | `query_adjust_history` | **未实现** |
| 6. AI 综合分析 + 建议 | 内置（LLM 推理） | 已实现 |
| 7. 模拟调整效果 | `simulate_adjust` | **未实现** |
| 8. 执行调整 | `adjust_thread_pool` | 已实现 |

**完备度：30%** — 只有查询当前配置和执行调整可用，缺少历史数据分析能力，AI 无法获取足够信息做出高质量优化建议。

---

### 5.3 场景：线程池故障诊断

**典型对话：** "order-service 线程池一直报拒绝，帮我排查一下"

| 步骤 | 所需工具 | 当前状态 |
|---|---|---|
| 1. 查询当前状态 | `query_thread_pool` | 已实现 |
| 2. 查询拒绝事件历史 | `query_rejection_log` | **未实现** |
| 3. 查询队列使用趋势 | `analyze_trends` | **未实现** |
| 4. 查询告警记录 | `query_alerts` | **未实现** |
| 5. 对比多实例指标 | `compare_instances` | **未实现** |
| 6. AI 综合诊断 | 内置（LLM 推理） | 已实现 |
| 7. 执行修复调整 | `adjust_thread_pool` | 已实现 |

**完备度：20%** — 只能看到当前瞬时指标，无法回溯历史趋势和告警事件，诊断能力严重不足。

---

### 5.4 场景：批量线程池运维

**典型对话：** "把所有服务的核心线程数都调大 50%"

| 步骤 | 所需工具 | 当前状态 |
|---|---|---|
| 1. 发现所有实例 | `list_instances` | 已实现 |
| 2. 查询所有线程池状态 | `list_thread_pools` | **未实现**（缺少批量查询） |
| 3. 批量调整 | `batch_adjust` | **未实现** |
| 4. 批量审批 | ApprovalService（需扩展） | **未实现** |

**完备度：15%** — 只有实例发现可用，批量操作能力完全缺失。

---

### 5.5 场景：定时巡检与自动调优

**典型对话：** "每天下午 2 点帮我检查一下所有线程池"

| 步骤 | 所需工具 | 当前状态 |
|---|---|---|
| 1. 创建定时任务 | `create_scheduled_task` | 已实现 |
| 2. 查看已有任务 | `list_scheduled_tasks` | **未实现** |
| 3. 暂停/恢复任务 | `pause_scheduled_task` / `resume_scheduled_task` | **未实现** |
| 4. 查看执行日志 | `query_task_logs` | **未实现** |
| 5. 删除任务 | `delete_scheduled_task` | **未实现** |

**完备度：25%** — 只能创建任务，无法管理和监控已有任务。

---

## 六、工具需求优先级排序

### 6.1 第一优先级：补全 DIAGNOSE 分类（影响优化和诊断场景）

| 工具名 | 分类 | 底层依赖 | 实现难度 |
|---|---|---|---|
| `query_history` | DIAGNOSE | ThreadPoolLogStore.query() | 低 — 直接包装 |
| `analyze_trends` | DIAGNOSE | ThreadPoolLogStore.summary() | 低 — 直接包装 |

**理由：** 这两个工具的底层能力已完全就绪，只需编写 Tool 类注册到 ToolRegistry。实现后 OPTIMIZE 和 DIAGNOSE 意图的核心链路即可打通。

### 6.2 第二优先级：补全 ADJUST 分类（影响修改场景的完整性）

| 工具名 | 分类 | 底层依赖 | 实现难度 |
|---|---|---|---|
| `rollback_thread_pool` | ADJUST | AuditStore + ThreadPoolQueryService | 中 — 需扩展 AuditStore 记录调整前快照 |
| `list_thread_pools` | QUERY | ThreadPoolQueryService.listPoolIds() | 低 — 直接包装 |
| `batch_adjust` | ADJUST | ThreadPoolQueryService（需扩展批量接口） | 中 — 需设计批量接口 |

**理由：** `list_thread_pools` 是 AI 操作线程池的入口，没有它 AI 无法引导用户。`rollback` 是修改操作的安全网。`batch_adjust` 已被权限系统预留，应尽快实现。

### 6.3 第三优先级：补全定时任务管理（影响巡检场景）

| 工具名 | 分类 | 底层依赖 | 实现难度 |
|---|---|---|---|
| `list_scheduled_tasks` | SYSTEM | ScheduledTaskService.list() | 低 |
| `query_task_logs` | SYSTEM | ScheduledTaskService.getExecutionLogs() | 低 |
| `delete_scheduled_task` | SYSTEM | ScheduledTaskService.delete() + TaskScheduler.stopTask() | 低 |
| `pause_scheduled_task` | SYSTEM | TaskScheduler.pauseTask() | 低 |
| `resume_scheduled_task` | SYSTEM | TaskScheduler.resumeTask() | 低 |

**理由：** 底层 CRUD 和调度控制已完整实现，只需逐一包装为 Tool。`create_scheduled_task` 已实现，但缺管理工具导致 AI 只能创建不能管理。

### 6.4 第四优先级：增强诊断能力

| 工具名 | 分类 | 底层依赖 | 实现难度 |
|---|---|---|---|
| `query_alerts` | DIAGNOSE | 需新增告警存储 | 高 — 缺底层存储 |
| `query_adjust_history` | DIAGNOSE | AuditStore（需扩展查询维度） | 中 |
| `query_rejection_log` | DIAGNOSE | 需新增拒绝分析记录存储 | 高 — 缺底层存储 |
| `simulate_adjust` | ADJUST | 纯计算，无副作用 | 中 — 需设计模拟模型 |

---

## 七、现有问题与改进建议

### 7.1 `create_scheduled_task` 缺少 ToolCategory

**现状：** `ToolCategory` 为 `null`，导致 `ToolSearchService` 按意图筛选时可能遗漏。

**建议：** 设置 `category = ToolCategory.SYSTEM`，与 `list_instances` 保持一致。

### 7.2 `create_scheduled_task` 的 AUTO_ADJUST 策略缺少审批

**现状：** 工具标记 `needsApproval=false`，但 `action=AUTO_ADJUST` 会自动调整线程池参数。

**建议：** 当 `action=AUTO_ADJUST` 时应强制走审批流程，或至少在 PermissionManager 中增加规则。

### 7.3 `list_instances` 的 `service_name` 参数未生效

**现状：** 参数定义了 `service_name`，但 executor 中调用 `discoveryService.discoverAllInstances()` 未使用该参数做过滤。

**建议：** 在 InstanceDiscoveryService 接口增加 `discoverByService(String serviceName)` 方法，或在工具 executor 内对结果进行过滤。

### 7.4 AuditStore 查询维度不足

**现状：** 只有 `getHistory(sessionId, limit)`，缺少按 threadPoolId / 时间范围 / 工具名查询。

**建议：** 扩展 AuditStore 增加多维度查询接口，支撑 `query_adjust_history` 工具。

### 7.5 调整操作缺少快照记录

**现状：** `adjust_thread_pool` 执行后，AuditStore 只记录了 ToolResult 的 summary，没有记录调整前的参数快照。

**建议：** 在 `adjustPool()` 返回值中确保包含完整的 `before` 参数，并在 AuditRecord 中持久化，支撑回滚功能。

### 7.6 告警和拒绝分析缺少持久化存储

**现状：** ThreadPoolAlarmChecker 只推送不存储，RejectionAnalysisListener 只在日志中输出和钉钉推送。

**建议：** 新增 `AlarmRecordStore` 和 `RejectionRecordStore`，以 JSON Lines 文件存储（复用 ThreadPoolLogStore 的模式），支撑 `query_alerts` 和 `query_rejection_log` 工具。

---

## 八、工具全景规划图

```
┌─────────────────────────────────────────────────────────────────┐
│                        QUERY 分类                                │
│  ✅ query_thread_pool     查询单个线程池运行时指标                  │
│  ✅ list_instances        列举所有服务实例                         │
│  📋 list_thread_pools     列举所有线程池 ID（待实现）               │
├─────────────────────────────────────────────────────────────────┤
│                        ADJUST 分类                               │
│  ✅ adjust_thread_pool    调整线程池参数（需审批）                  │
│  📋 batch_adjust          批量调整（权限/风险已预留，待实现）        │
│  📋 rollback_thread_pool  回滚到上次配置（待实现）                  │
│  📋 simulate_adjust       模拟调整效果（待实现）                    │
├─────────────────────────────────────────────────────────────────┤
│                       DIAGNOSE 分类                              │
│  📋 query_history         查询历史运行指标（底层已就绪）             │
│  📋 analyze_trends        趋势分析与摘要统计（底层已就绪）          │
│  📋 query_alerts          查询告警记录（需新增存储）                │
│  📋 query_adjust_history  查询调整历史（需扩展 AuditStore）        │
│  📋 query_rejection_log   查询拒绝分析记录（需新增存储）            │
├─────────────────────────────────────────────────────────────────┤
│                        SYSTEM 分类                               │
│  ✅ create_scheduled_task 创建定时任务（需补充 ToolCategory）       │
│  📋 list_scheduled_tasks  查询所有定时任务（底层已就绪）            │
│  📋 delete_scheduled_task 删除定时任务（底层已就绪）                │
│  📋 pause_scheduled_task  暂停定时任务（底层已就绪）               │
│  📋 resume_scheduled_task 恢复定时任务（底层已就绪）               │
│  📋 query_task_logs       查询任务执行日志（底层已就绪）            │
│  📋 health_check          系统健康检查（待设计）                    │
└─────────────────────────────────────────────────────────────────┘

图例：✅ 已实现  📋 待实现
```

---

## 九、实现路径建议

### Phase 1：零成本补全（底层已就绪，只需包装）

直接基于现有服务方法编写 Tool 类并注册：

| 工具 | 底层方法 | 预估代码量 |
|---|---|---|
| `query_history` | `ThreadPoolLogStore.query()` | ~80 行 |
| `analyze_trends` | `ThreadPoolLogStore.summary()` | ~80 行 |
| `list_thread_pools` | `ThreadPoolQueryService.listPoolIds()` | ~60 行 |
| `list_scheduled_tasks` | `ScheduledTaskService.list()` | ~60 行 |
| `delete_scheduled_task` | `ScheduledTaskService.delete()` + `TaskScheduler.stopTask()` | ~70 行 |
| `pause_scheduled_task` | `TaskScheduler.pauseTask()` | ~60 行 |
| `resume_scheduled_task` | `TaskScheduler.resumeTask()` | ~60 行 |
| `query_task_logs` | `ScheduledTaskService.getRecentExecutionLogs()` | ~70 行 |

### Phase 2：中等投入（需扩展现有服务）

| 工具 | 需要的扩展 | 预估代码量 |
|---|---|---|
| `query_adjust_history` | AuditStore 增加多维度查询 | 工具 ~80 行 + 扩展 ~50 行 |
| `batch_adjust` | ThreadPoolQueryService 增加批量接口 | 工具 ~120 行 + 扩展 ~80 行 |
| `rollback_thread_pool` | AuditStore 记录调整前快照 + 回滚逻辑 | 工具 ~100 行 + 扩展 ~60 行 |

### Phase 3：高投入（需新增基础设施）

| 工具 | 需要新增的基础设施 | 预估代码量 |
|---|---|---|
| `query_alerts` | AlarmRecordStore 告警持久化 | 存储 ~150 行 + 工具 ~80 行 |
| `query_rejection_log` | RejectionRecordStore 拒绝记录持久化 | 存储 ~150 行 + 工具 ~80 行 |
| `simulate_adjust` | 模拟引擎（基于历史数据推算） | 引擎 ~200 行 + 工具 ~100 行 |
| `health_check` | 健康评估规则引擎 | 引擎 ~150 行 + 工具 ~80 行 |

---

## 十、附录

### A. 文件索引

| 文件 | 路径 | 职责 |
|---|---|---|
| ToolRegistry | `anticipa-agent/.../ToolRegistry.java` | 工具注册表 |
| ToolDefinition | `anticipa-agent/.../ToolDefinition.java` | 工具定义模型 |
| ToolResult | `anticipa-agent/.../ToolResult.java` | 工具执行结果 |
| ToolCategory | `anticipa-agent/.../tool/ToolCategory.java` | 工具分类枚举 |
| ToolSearchService | `anticipa-agent/.../tool/ToolSearchService.java` | 按意图检索工具 |
| QueryThreadPoolTool | `anticipa-agent/.../tool/QueryThreadPoolTool.java` | 查询线程池 |
| AdjustThreadPoolTool | `anticipa-agent/.../tool/AdjustThreadPoolTool.java` | 调整线程池 |
| ListInstancesTool | `anticipa-agent/.../discovery/ListInstancesTool.java` | 列举实例 |
| CreateScheduledTaskTool | `anticipa-agent/.../scheduled/CreateScheduledTaskTool.java` | 创建定时任务 |
| ThreadPoolQueryService | `anticipa-agent/.../discovery/ThreadPoolQueryService.java` | 查询服务抽象接口 |
| InstanceDiscoveryService | `anticipa-agent/.../discovery/InstanceDiscoveryService.java` | 实例发现接口 |
| PermissionManager | `anticipa-agent/.../permission/PermissionManager.java` | Deny-First 权限管理 |
| RiskEvaluator | `anticipa-agent/.../permission/RiskEvaluator.java` | 风险评估 |
| ApprovalService | `anticipa-agent/.../approval/ApprovalService.java` | 审批服务 |
| AuditStore | `anticipa-agent/.../audit/AuditStore.java` | 审计记录 |
| MemoryManager | `anticipa-agent/.../memory/MemoryManager.java` | 记忆管理 |
| SystemPromptBuilder | `anticipa-agent/.../prompt/SystemPromptBuilder.java` | Prompt 构建 |
| AgentLoop | `anticipa-agent/.../AgentLoop.java` | Agent 核心引擎 |
| Intent | `anticipa-agent/.../intent/Intent.java` | 意图枚举 |
| ThreadPoolLogStore | `anticipa-core/.../monitor/ThreadPoolLogStore.java` | 日志存储与查询 |
| ThreadPoolLogRecord | `anticipa-core/.../monitor/ThreadPoolLogRecord.java` | 日志记录模型 |
| ThreadPoolAlarmChecker | `anticipa-core/.../alarm/ThreadPoolAlarmChecker.java` | 告警检查 |
| RejectionAnalysisListener | `anticipa-agent/.../event/RejectionAnalysisListener.java` | 拒绝分析监听 |
| ScheduledTaskService | `anticipa-agent/.../scheduled/ScheduledTaskService.java` | 定时任务 CRUD |
| TaskScheduler | `anticipa-agent/.../scheduled/TaskScheduler.java` | 定时任务调度 |
| ScheduledTask | `anticipa-agent/.../scheduled/ScheduledTask.java` | 定时任务模型 |

### B. 统计摘要

| 维度 | 数量 |
|---|---|
| 已实现工具 | 4 |
| 待实现工具（底层已就绪） | 8 |
| 待实现工具（需扩展） | 3 |
| 待实现工具（需新增基础设施） | 4 |
| 工具分类 | 4 (QUERY / ADJUST / DIAGNOSE / SYSTEM) |
| 意图类型 | 8 (QUERY / ADJUST / DIAGNOSE / OPTIMIZE / EXPLAIN / CHAT / BATCH_OPERATE / SCHEDULE_TASK) |
| DIAGNOSE 分类已有工具数 | **0** |

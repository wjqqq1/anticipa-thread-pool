---
title: 任务拒绝问题排查
type: knowledge
category: diagnose
tags: [任务拒绝, 排查, 队列满, 线程数]
priority: P0
source: industry
---

# 任务拒绝问题排查

## 症状
- 收到 RejectedExecutionException 异常
- 业务出现中断或降级
- 告警系统触发拒绝告警

## 排查步骤

### Step 1：确认拒绝计数
通过 Anticipa 监控面板查看各线程池的拒绝计数，确认哪个线程池在拒绝任务。

### Step 2：区分配置类拒绝 vs 运行时拒绝

**配置类拒绝**：corePoolSize 和队列容量设置过小，系统正常运行时也在拒绝。
**解决方案**：增大 maximumPoolSize 和/或队列容量。

**运行时拒绝**：流量突增导致瞬时超载。
**解决方案**：限流或扩容。

### Step 3：检查线程和队列状态

| 状态 | 含义 | 解决 |
|---|---|---|
| `active = max 且 queue 满` | 线程和队列都用完了 | 需要扩容 |
| `active < max 但 queue 满` | 线程扩容太慢 | 缩短 keepAliveTime 或预热线程 |
| `active = max 但 queue 不满` | 队列满了但线程未满 | 扩大队列容量 |

### Step 4：检查拒绝策略
确认当前使用的拒绝策略是否合理：
- `AbortPolicy`：直接抛异常（需调用方 catch）
- `CallerRunsPolicy`：减慢生产速度（推荐）
- `DiscardPolicy`：静默丢弃（使用需谨慎）

## 修复方案

### 短期（立即止损）
1. 临时增加 maximumPoolSize 和队列容量
2. 如果拒绝策略是 AbortPolicy，立即改为 CallerRunsPolicy

### 中期（优化配置）
1. 根据历史监控数据重新计算容量需求
2. 调整 corePoolSize 到合理值
3. 开启 Anticipa 的自动告警

### 长期（架构优化）
1. 增加限流措施（Sentinel 等）
2. 考虑异步化改造，降低对线程池的瞬时冲击
3. 拆分线程池，隔离不同优先级任务

## 注意事项
- 拒绝不总是坏事——有时是系统自我保护
- 拒绝后要有兜底逻辑（降级、重试、补偿）
- 记录每次拒绝的上下文，方便后续分析

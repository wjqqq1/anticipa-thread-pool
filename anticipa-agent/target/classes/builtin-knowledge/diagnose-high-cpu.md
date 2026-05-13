---
title: 高 CPU 场景诊断
type: knowledge
category: diagnose
tags: [高CPU, 诊断, 线程数, 上下文切换]
priority: P0
source: industry
---

# 高 CPU 场景诊断

## 症状
- CPU 利用率持续 > 90%
- 系统响应变慢
- 可能伴随任务拒绝

## 常见原因

### 原因 1：线程数过多
**现象**：活跃线程 = maximumPoolSize，CPU 利用率高但吞吐量低。
**根因**：线程数超过 CPU 核数过多，上下文切换开销 > 实际计算收益。

**诊断命令**：
```bash
# 查看上下文切换频率
vmstat 1 5
# 观察 cs（context switch）列，过高说明切换频繁

# 查看线程数
ps -eLf | wc -l
```

**解决方案**：适当减小 maximumPoolSize，减少线程争抢。

### 原因 2：任务本身是 CPU 密集型的
**现象**：活跃线程 < corePoolSize，但 CPU 利用率很高。
**根因**：每个任务都在大量使用 CPU，无需过多线程。

**解决方案**：使用 CPU 密集型的配置（corePoolSize = CPU核数 + 1）。

### 原因 3：死循环或无界递归
**现象**：单个线程 CPU 使用率 100%，持续不退。
**根因**：代码 bug 导致死循环或无限递归。

**诊断**：
```bash
# 找到 CPU 最高的线程
top -H
# 导出线程栈
jstack <pid> > threaddump.txt
```

### 原因 4：频繁的锁竞争
**现象**：大量线程处于 BLOCKED 状态。
**根因**：多个线程竞争同一个锁，导致大量上下文切换。

**诊断**：jstack 分析，查找 BLOCKED 状态的线程和锁对象。

## 排查步骤
1. 确认 CPU 利用率（top 或监控面板）
2. 确认活跃线程数和队列深度（Anticipa 监控）
3. 如果活跃线程 = maximumPoolSize → 线程数过多
4. 如果活跃线程 < corePoolSize → 任务本身重计算
5. jstack 分析线程状态
6. 根据分析结果调整配置或修复代码

---
title: 线程池常见配置陷阱
type: knowledge
category: best-practice
tags: [陷阱, 常见问题, 线程池, OOM]
priority: P1
source: industry
---

# 线程池常见配置陷阱

## 陷阱 1：无界队列导致 OOM
**错误用法**：
```java
new ThreadPoolExecutor(10, 20, 60, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>());  // 无界队列！
```
**后果**：任务持续堆积，队列占用无限增长，最终 OOM。

**正确做法**：始终使用有界队列，并设置合理的容量上限。

## 陷阱 2：线程数过大导致系统崩溃
**错误用法**：将 maximumPoolSize 设置得非常大（如 10000）。
**后果**：每个线程默认栈 1MB，10000 线程约占用 10GB 内存。

**正确做法**：maximumPoolSize 不超过 200（大多数场景），参考 CPU 核数 × 4。

## 陷阱 3：corePoolSize == maximumPoolSize
**错误用法**：
```yaml
core-pool-size: 20
maximum-pool-size: 20
```
**后果**：队列满了也无法扩线程应对突发流量，只能拒绝。
**正确做法**：保留 1.5~3 倍的弹性空间。

## 陷阱 4：忽略 rejectedHandler
**错误用法**：使用默认的 AbortPolicy 但不捕获异常。
**后果**：任务被静默丢弃（如果上层未捕获异常）。

**正确做法**：使用 CallerRunsPolicy 或自定义策略并记录告警。

## 陷阱 5：线程池用完不关闭
**错误用法**：每次请求创建新的线程池，用完不 shutdown。
**后果**：线程泄露，最终资源耗尽。

**正确做法**：线程池作为全局单例管理，复用而非创建。

## 陷阱 6：队列太小导致频繁拒绝
**错误用法**：队列容量设置过小（如 10），即使线程池未满载也频繁拒绝。
**后果**：吞吐量远低于系统真实容量。

**正确做法**：队列容量 = 预估 QPS × 响应时间 SLA，留有安全余量。

## 陷阱 7：keepAliveTime 过短
**错误用法**：keepAliveTime = 1 秒。
**后果**：空闲线程被快速回收，流量波动时反复创建/销毁线程。

**正确做法**：IO 密集型 60~120 秒，CPU 密集型 5~30 秒。

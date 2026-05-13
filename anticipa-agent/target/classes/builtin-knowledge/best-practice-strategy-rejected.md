---
title: 拒绝策略选择指南
type: knowledge
category: best-practice
tags: [拒绝策略, 线程池, CallerRunsPolicy, AbortPolicy]
priority: P0
source: industry
---

# 拒绝策略选择指南

## 概述
当线程池和队列都满时，拒绝策略决定如何处理新提交的任务。Java 内置 4 种策略，各有适用场景。

## 策略对比

| 策略 | 行为 | 适用场景 | 风险 |
|---|---|---|---|
| **AbortPolicy（默认）** | 抛出 RejectedExecutionException | 调用方必须感知拒绝 | 未捕获异常导致中断 |
| **CallerRunsPolicy** | 提交任务的线程自己执行 | 大多数场景推荐 | 提交线程被阻塞 |
| **DiscardPolicy** | 丢弃任务，无提示 | 不重要的定时任务 | 任务丢失不被感知 |
| **DiscardOldestPolicy** | 丢弃队列中最旧的任务 | 实时性高的场景 | 旧任务丢失 |

## 推荐方案

### CallerRunsPolicy（首选）
让提交任务的线程自己执行被拒绝的任务，天然实现**背压**：
- 生产速度 > 消费速度 → 提交者线程也被占用 → 生产速度自动降低
- 不需要额外处理逻辑
- 适用于大多数业务场景

### 自定义策略
当内置策略不满足时，可实现 RejectedExecutionHandler：

```java
public class CustomRejectedHandler implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        // 1. 记录告警
        // 2. 尝试写入 MQ/DB 做延迟处理
        // 3. 或者降级到其他服务
    }
}
```

## 注意事项
- AbortPolicy 一定要配合 try-catch 使用
- CallerRunsPolicy 可能导致提交线程长时间阻塞，需考虑超时机制
- 拒绝策略触发时，一定要记录日志和告警

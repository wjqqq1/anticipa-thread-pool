---
title: IO 密集型线程池最佳实践
type: knowledge
category: best-practice
tags: [IO密集型, 线程池, 配置建议]
businessType: IO_INTENSIVE
priority: P0
source: industry
---

# IO 密集型线程池最佳实践

## 适用场景
适用于数据库访问、RPC 调用、HTTP 请求、文件读写、消息队列消费等以 IO 等待为主的业务。线程大部分时间在等待外部资源，CPU 利用率不高。

## 核心建议
- **corePoolSize = CPU 核数 × 2**（或 CPU 核数 × 2 + 1）
- **maximumPoolSize = CPU 核数 × 4**（不超过 200，视硬件而定）
- **队列建议使用 ResizableCapacityLinkedBlockingQueue**（可动态调整容量）
- **队列容量 = 预估 QPS × 响应时间 SLA（秒）** — 如 QPS=1000, SLA=2s → 队列容量=2000
- **keepAliveTime** 建议 60~120 秒（IO 密集型线程创建成本高，不宜频繁回收）
- **拒绝策略建议 CallerRunsPolicy**（降级而非丢弃）

## 原理说明
IO 密集型线程在大部分时间处于等待 IO 状态，CPU 利用率不高。增加线程数能提高 CPU 利用率，但过多线程会导致：
1. 线程创建/销毁的开销
2. 上下文切换成本
3. 系统资源（内存、文件描述符）耗尽

## 典型配置示例
```yaml
- thread-pool-id: message-consumer
  core-pool-size: 32          # 16 核 × 2
  maximum-pool-size: 64       # 16 核 × 4
  queue-capacity: 5000
  keep-alive-time: 90
  rejected-handler: CallerRunsPolicy
  business-type: IO_INTENSIVE
```

## 监控要点
- **活跃线程数**：如果活跃线程长期远低于 corePoolSize，说明线程过多浪费资源
- **等待耗时/执行耗时比**：IO 密集型此比例应 > 3:1
- **数据库连接池**：确保线程池大小不超过数据库连接池上限

## 注意事项
- 避免线程数超过数据库连接池上限（如 MySQL 默认 200 连接）
- 监控实际活跃线程数，若长期等于 maximumPoolSize 则需扩容
- 线程过多会导致大量内存被线程栈占用（每线程默认栈 1MB，200 线程约 200MB）

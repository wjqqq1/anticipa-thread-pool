---
title: CPU 密集型线程池最佳实践
type: knowledge
category: best-practice
tags: [CPU密集型, 线程池, 配置建议]
businessType: CPU_INTENSIVE
priority: P0
source: industry
---

# CPU 密集型线程池最佳实践

## 适用场景
适用于图像处理、视频编码、科学计算、加密解密、数据压缩等以 CPU 计算为主的业务。线程大部分时间在执行计算，而非等待 IO。

## 核心建议
- **corePoolSize = CPU 核数 + 1**（防止页缺失等偶尔暂停导致线程空闲）
- **maximumPoolSize = CPU 核数 × 2**（不超过此值，过多线程反而降低性能）
- **队列建议使用 SynchronousQueue 或 ResizableCapacityLinkedBlockingQueue（小容量）**
- **队列容量** = 100~500（CPU 密集型不需要大队列缓存任务）
- **keepAliveTime** 建议 5~30 秒（CPU 密集型线程可快速回收）
- **拒绝策略建议 CallerRunsPolicy**（让调用线程执行，反向压制动）

## 原理说明
CPU 密集型的核心原则是**线程数 ≈ CPU 核数**。当线程数超过 CPU 核数时，频繁的上下文切换会显著降低吞吐量。每增加一个线程，就多一次上下文切换的成本。

## 典型配置示例
```yaml
- thread-pool-id: image-processor
  core-pool-size: 17          # 16 核 CPU + 1
  maximum-pool-size: 32       # 16 核 × 2
  queue-capacity: 200
  keep-alive-time: 15
  rejected-handler: CallerRunsPolicy
  business-type: CPU_INTENSIVE
```

## 监控要点
- **活跃线程数**：如果长期等于 corePoolSize，说明 CPU 利用率充分
- **队列深度**：CPU 密集型场景队列应基本为空，队列持续堆积说明线程数不足
- **上下文切换频率**：`vmstat 1` 观察 `cs` 列，过高说明线程过多

## 注意事项
- CPU 密集型不建议使用 LinkedBlockingQueue 无界队列（任务堆积会导致 OOM）
- 如果业务中有部分 IO 操作（如写日志、上报指标），实际线程数可适当增加
- 需要结合实际的 CPU 使用率监控进行调整，公式只是起点

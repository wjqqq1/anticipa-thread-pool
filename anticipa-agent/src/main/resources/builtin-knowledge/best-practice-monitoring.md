---
title: 线程池监控指标解读
type: knowledge
category: best-practice
tags: [监控, 指标, 告警阈值, 活跃线程, 队列]
priority: P1
source: industry
---

# 线程池监控指标解读

## 核心指标

### 活跃线程数（activeCount）
**含义**：当前正在执行任务的线程数。
**正常范围**：`activeCount < maximumPoolSize`
**告警阈值**：`activeCount >= maximumPoolSize × 0.8` 持续 5 分钟 → 警告
**严重阈值**：`activeCount == maximumPoolSize` 且队列持续增长 → 需要扩容

### 队列使用率（queueSize / queueCapacity）
**含义**：队列中等待的任务占队列容量的比例。
**正常范围**：`使用率 < 60%`
**警告**：`> 80%`
**严重**：`> 95%` 且持续增长

### 拒绝率（rejectCount / totalTaskCount）
**含义**：被拒绝的任务占总任务的比例。
**目标**：`拒绝率 = 0`
**警告**：出现任何拒绝都应告警（说明系统已达极限）

### 已完成任务数（completedTaskCount）
**含义**：系统启动以来已处理的任务总数。
**用途**：计算吞吐量 = `(currentCT - lastCT) / 采集间隔`

## 指标组合分析

| 活跃线程 | 队列情况 | CPU 利用率 | 结论 |
|---|---|---|---|
| 低 | 空 | 低 | 系统空闲，配置正常 |
| 低 | 堆积 | 低 | 线程数不足（IO 密集型典型症状） |
| 高 | 空 | 高 | CPU 密集型，正常 |
| 高 | 堆积 | 高 | 系统容量已达上限，需要扩容 |

## 采集策略
- 基础指标：每 10 秒采集一次
- 告警评估：每次采集后立即评估
- 趋势分析：每 5 分钟聚合一次做趋势判断
- 历史留存：7 天原始数据，30 天聚合数据

## 注意事项
- 拒绝计数需要计算增量（△rejectCount），而非累计值
- completedTaskCount 是单调递增的，用于计算差值的基准
- 队列剩余容量（remainingCapacity）比已使用量更有预测价值

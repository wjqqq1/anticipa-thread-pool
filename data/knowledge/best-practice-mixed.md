---
title: 混合型业务线程池配置建议
type: knowledge
category: best-practice
tags: [混合型, 线程池, 配置建议]
businessType: MIXED
priority: P1
source: industry
---

# 混合型业务线程池配置建议

## 适用场景
业务中既有 CPU 计算也有 IO 等待混合的场景，难以单纯归类为 CPU 密集型或 IO 密集型。例如：Web 服务处理请求（解析→业务计算→数据库查询→序列化返回）。

## 核心建议
- **corePoolSize = CPU 核数 × 1.5 ~ 2**
- **maximumPoolSize = CPU 核数 × 3 ~ 4**
- **队列容量折中**，介于纯 CPU 和纯 IO 之间
- **keepAliveTime** 30~60 秒
- **关键是观察**：实际运行中线程处于 WAITING（IO）和 RUNNABLE（CPU）的比例

## 调优方法
1. 先按 IO 密集型配置，运行一段时间
2. 观察活跃线程数 + CPU 利用率
3. 如果 CPU 利用率 > 80% 但活跃线程仍 < corePoolSize，说明偏向 CPU 密集型，减少线程数
4. 如果 CPU 利用率 < 50% 且活跃线程 = maximumPoolSize，说明偏向 IO 密集型，增加线程数

## 典型场景
- Web 应用 Tomcat 线程池
- API 网关处理线程池
- 数据处理中间件

## 注意事项
- 混合型没有固定的万能公式，必须通过监控持续调优
- 建议从 IO 密集型配置起步，逐步下调到合理的值

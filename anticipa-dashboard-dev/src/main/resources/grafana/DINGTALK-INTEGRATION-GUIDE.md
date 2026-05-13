# 钉钉告警集成指南

本文档介绍如何为 Anticipa 动态线程池框架配置钉钉（DingTalk）机器人告警通知，实现线程池异常实时推送。

---

## 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                    告警触发源                             │
│  ┌──────────────┐ ┌──────────────┐ ┌────────────────┐  │
│  │ 阈值告警检测  │ │ 配置变更监听  │ │ 拒绝策略告警   │  │
│  │ AlarmChecker │ │ RefreshListener│ │ RejectedHandler│  │
│  └──────┬───────┘ └──────┬───────┘ └───────┬────────┘  │
│         │                │                 │            │
│         ▼                ▼                 ▼            │
│  ┌──────────────────────────────────────────────────┐  │
│  │              NotifierDispatcher（分发器）           │  │
│  │         + AlarmRateLimiter（去重限频）              │  │
│  └──────────────────────┬───────────────────────────┘  │
│                         │                               │
│                         ▼                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │          DingTalkMessageService                   │  │
│  │    构建 Markdown 消息 → HTTP POST 到 Webhook      │  │
│  └──────────────────────┬───────────────────────────┘  │
└─────────────────────────┼───────────────────────────────┘
                          │
                          ▼
                 ┌────────────────┐
                 │  钉钉群机器人    │
                 │  推送告警消息    │
                 └────────────────┘
```

---

## 一、创建钉钉群机器人

### 1.1 在钉钉群中添加机器人

1. 打开钉钉 PC 端或移动端，进入需要接收告警的**钉钉群**
2. 点击群设置 → **智能群助手** → **添加机器人** → **自定义**
3. 填写机器人名称（如 `线程池告警机器人`）
4. 安全设置选择以下任一方式：
   - **加签**（推荐）：生成 Secret 签名密钥
   - **IP 地址段**：限制请求来源 IP
   - **自定义关键词**：消息中需包含指定关键词

5. 点击完成后，获取以下信息：
   - **Webhook 地址**：`https://oapi.dingtalk.com/robot/send?access_token=xxxxx`
   - **加签 Secret**（如选加签方式）：`SECxxxxxxxxx`

### 1.2 Webhook URL 格式

```
https://oapi.dingtalk.com/robot/send?access_token=<你的access_token>
```

> **注意**：access_token 在创建机器人后自动生成，每个机器人的 token 唯一。

---

## 二、配置 Anticipa 钉钉通知

Anticipa 支持两个独立的钉钉通知通道，可按需配置：

### 2.1 通用告警通道（线程池阈值告警 + 配置变更通知）

在线程池配置文件（Nacos / Apollo / 本地 YAML）中添加：

```yaml
anticipa:
  # ── 通知平台配置 ──
  notify-platforms:
    platform: DING
    url: https://oapi.dingtalk.com/robot/send?access_token=你的access_token

  # ── 线程池配置（每个线程池可单独设置告警） ──
  executors:
    - thread-pool-id: anticipa-producer
      # ... 其他线程池参数 ...
      notify:
        receives: "138xxxx|张三"       # @指定人（手机号或用户ID，多个用 | 分隔）
        interval: 5                    # 同一线程池告警间隔（分钟），防刷屏
      alarm:
        enable: true                   # 启用告警
        queue-threshold: 80            # 队列使用率告警阈值（%）
        active-threshold: 80           # 活跃线程占比告警阈值（%）

    - thread-pool-id: anticipa-consumer
      notify:
        receives: ""
        interval: 10
      alarm:
        enable: true
        queue-threshold: 90
        active-threshold: 90
```

### 2.2 拒绝策略专用通道（AI 拒绝分析告警）

当线程池触发拒绝策略时，可独立发送钉钉通知：

```yaml
anticipa:
  rejected-analysis:
    enabled: true                     # 启用 AI 拒绝分析
    policy-name: AiAnalysisRejectedPolicy
    ding-talk:
      enabled: true                   # 启用钉钉拒绝告警
      webhook-url: https://oapi.dingtalk.com/robot/send?access_token=你的access_token
      secret: SECxxxxxxxxx            # 加签密钥（可选，与机器人安全设置对应）
      title: "🚨 线程池拒绝告警"       # 消息标题
      notify-on-reject: true          # 每次拒绝都发送通知
```

> **说明**：如果拒绝策略通道与通用通道使用同一个 Webhook，告警消息会发送到同一个群；如需分离，可创建不同的钉钉机器人。

---

## 三、告警类型与消息格式

### 3.1 线程池阈值告警

**触发条件**（由 `ThreadPoolAlarmChecker` 定期检测）：

| 告警类型 | 触发条件 | 配置项 |
|---------|---------|-------|
| **活跃线程告警** | `activeCount / maximumPoolSize × 100 ≥ active-threshold` | `alarm.active-threshold` |
| **队列容量告警** | `queueSize / queueCapacity × 100 ≥ queue-threshold` | `alarm.queue-threshold` |

**钉钉消息格式**（Markdown）：

```
🚨 线程池告警通知

**应用名称**: nacos-cloud-example
**线程池ID**: anticipa-consumer
**实例IP**: 192.168.1.100:8080
**告警类型**: 活跃线程告警 / 队列容量告警

**核心线程数**: 10
**最大线程数**: 20
**当前线程数**: 18
**活跃线程数**: 17
**历史最大线程数**: 20

**队列类型**: ResizableCapacityLinkedBlockingQueue
**队列容量**: 500
**当前队列大小**: 420
**队列剩余容量**: 80

**拒绝策略**: CallerRunsPolicy
**拒绝次数**: 3
**接收人**: 138xxxx
**告警间隔**: 5 分钟
**告警时间**: 2026-05-05 18:30:00
```

### 3.2 配置变更通知

**触发条件**（由 `DynamicThreadPoolRefreshListener` 监听 Nacos/Apollo 配置变更）：

| 监听字段 | 说明 |
|---------|------|
| `corePoolSize` | 核心线程数变更 |
| `maximumPoolSize` | 最大线程数变更 |
| `keepAliveTime` | 空闲存活时间变更 |
| `rejectedHandler` | 拒绝策略变更 |
| 队列相关 | 队列类型/容量变更 |

**钉钉消息格式**（Markdown）：

```
⚙️ 线程池配置变更通知

**应用名称**: nacos-cloud-example
**线程池ID**: anticipa-consumer
**实例IP**: 192.168.1.100:8080

**核心线程数**: 10 → 15
**最大线程数**: 20 → 30
**空闲存活时间**: 300 → 600

**队列类型**: ResizableCapacityLinkedBlockingQueue
**队列容量**: 500

**旧拒绝策略**: CallerRunsPolicy
**新拒绝策略**: AbortPolicy

**接收人**: 138xxxx
**变更时间**: 2026-05-05 18:35:00
```

### 3.3 拒绝策略 AI 分析告警

**触发条件**（由 `AiAnalysisRejectedHandler` 捕获拒绝事件）：

当线程池发生任务拒绝时，AI 模块会分析拒绝原因并推送钉钉通知。

---

## 四、去重与限频机制

`AlarmRateLimiter` 防止同一线程池在短时间内重复发送告警：

- 每个线程池独立跟踪上次告警时间
- 通过 `notify.interval` 配置最小告警间隔（单位：分钟，默认 5 分钟）
- 仅当距上次告警超过间隔时，才发送新告警

```
时间轴示例（interval=5 分钟）：

18:00  队列超阈值 → ✅ 发送告警
18:02  队列仍然超阈值 → ❌ 限频跳过
18:06  队列仍然超阈值 → ✅ 发送告警（距上次已超 5 分钟）
```

---

## 五、配置参数汇总

### 5.1 通用通知平台配置

| 配置项 | 路径 | 说明 | 默认值 |
|-------|------|------|-------|
| 通知平台 | `anticipa.notify-platforms.platform` | 通知类型，固定填 `DING` | — |
| Webhook URL | `anticipa.notify-platforms.url` | 钉钉机器人完整 Webhook 地址 | — |

### 5.2 线程池级告警配置

| 配置项 | 路径 | 说明 | 默认值 |
|-------|------|------|-------|
| 启用告警 | `executors[].alarm.enable` | 是否开启告警检测 | `true` |
| 队列阈值 | `executors[].alarm.queue-threshold` | 队列使用率告警阈值（%） | `80` |
| 活跃阈值 | `executors[].alarm.active-threshold` | 活跃线程占比告警阈值（%） | `80` |
| 接收人 | `executors[].notify.receives` | @人列表（手机号，多个用 `\|` 分隔） | — |
| 告警间隔 | `executors[].notify.interval` | 最小告警间隔（分钟） | `5` |

### 5.3 拒绝策略告警配置

| 配置项 | 路径 | 说明 | 默认值 |
|-------|------|------|-------|
| 启用 | `anticipa.rejected-analysis.ding-talk.enabled` | 启用钉钉拒绝告警 | `true` |
| Webhook | `anticipa.rejected-analysis.ding-talk.webhook-url` | 拒绝告警专用 Webhook | — |
| 加签密钥 | `anticipa.rejected-analysis.ding-talk.secret` | 钉钉加签密钥 | — |
| 标题 | `anticipa.rejected-analysis.ding-talk.title` | 消息标题 | `🚨 线程池拒绝告警` |
| 拒绝即通知 | `anticipa.rejected-analysis.ding-talk.notify-on-reject` | 每次拒绝都发送 | `true` |

---

## 六、完整配置示例

以下为一个 Nacos 云配置的完整示例：

```yaml
anticipa:
  enable: true

  # 通用钉钉告警 Webhook
  notify-platforms:
    platform: DING
    url: https://oapi.dingtalk.com/robot/send?access_token=abc123def456

  # AI 拒绝分析 + 钉钉通知
  rejected-analysis:
    enabled: true
    policy-name: AiAnalysisRejectedPolicy
    ding-talk:
      enabled: true
      webhook-url: https://oapi.dingtalk.com/robot/send?access_token=abc123def456
      secret: SECmySecretKey123
      title: "🚨 线程池拒绝告警"
      notify-on-reject: true

  # 线程池定义
  executors:
    - thread-pool-id: anticipa-producer
      core-pool-size: 10
      maximum-pool-size: 20
      queue-capacity: 500
      keep-alive-time: 300
      rejected-handler: CallerRunsPolicy
      notify:
        receives: "13800138000|张三"
        interval: 5
      alarm:
        enable: true
        queue-threshold: 80
        active-threshold: 80

    - thread-pool-id: anticipa-consumer
      core-pool-size: 5
      maximum-pool-size: 15
      queue-capacity: 200
      keep-alive-time: 600
      rejected-handler: AbortPolicy
      notify:
        receives: ""
        interval: 10
      alarm:
        enable: true
        queue-threshold: 90
        active-threshold: 90
```

---

## 七、扩展：自定义通知渠道

Anticipa 的通知体系基于 `NotifierService` 接口设计，支持扩展：

```java
public interface NotifierService {
    void sendAlarmMessage(ThreadPoolAlarmNotifyDTO alarmDTO);
    void sendChangeMessage(ThreadPoolConfigChangeDTO changeDTO);
    void sendWebChangeMessage(WebThreadPoolConfigChangeDTO changeDTO);
}
```

**添加新渠道（如企业微信、邮件）步骤**：

1. 实现 `NotifierService` 接口
2. 添加 `@Service` 或在配置类中注册为 Bean
3. `NotifierDispatcher` 会自动发现并分发到所有实现类

---

## 八、常见问题

### Q1: 配置了 Webhook 但收不到消息

1. 确认 Webhook URL 可达：`curl -X POST '<webhook_url>' -H 'Content-Type: application/json' -d '{"msgtype":"text","text":{"content":"测试"}}'`
2. 检查安全设置是否匹配：如果机器人设置了加签，需配置 `secret`；如果设置了关键词，消息中需包含关键词
3. 查看应用日志，搜索 `[DingTalkMessageService]` 相关输出

### Q2: 告警消息过多刷屏

1. 调大 `notify.interval`（如从 5 分钟改为 30 分钟）
2. 调高 `alarm.queue-threshold` 和 `alarm.active-threshold`
3. 对非关键线程池设置 `alarm.enable: false`

### Q3: 配置变更后是否需要重启

- 通用告警通道（`notify-platforms`）的 `url` 变更需重启应用
- 线程池级别的告警配置（`alarm` / `notify`）通过 Nacos/Apollo 动态刷新，无需重启

### Q4: 如何只接收拒绝告警，不接收阈值告警

```yaml
executors:
  - thread-pool-id: your-pool
    alarm:
      enable: false          # 关闭阈值告警
    # rejected-analysis 的钉钉通知仍然生效
```

### Q5: 多个应用能否共用一个钉钉机器人

可以。不同应用的 `application_name` 和 `threadPoolId` 不同，告警消息中会标注来源。但建议生产环境为不同应用创建独立机器人，便于消息分流。

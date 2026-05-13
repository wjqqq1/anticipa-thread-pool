# Anticipa 动态线程池

基于 AI 智能调优的动态线程池框架 —— Agent + Tools 驱动的智能线程池运维平台。

## 技术栈

| 技术 | 版本 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.0.7 |
| Spring Cloud | 2022.0.3 |
| Spring Cloud Alibaba | 2022.0.0.0-RC2 |
| Hutool | 5.8.37 |
| Fastjson2 | 2.0.57 |
| Sa-Token | 1.43.0 |
| OkHttp | 4.12.0 |

## 项目模块

项目采用模块化设计，共包含 9 个模块。下面按照依赖层级从底层到上层逐一说明。

### 模块依赖关系

```
anticipa-core                          ← 最底层，纯 Java 核心
    ↑
anticipa-spring-base                   ← Spring 集成基础
    ↑
anticipa-common-spring-boot-starter    ← 公共自动装配
    ↑
    ├── anticipa-nacos-spring-boot-starter     ← Nacos 配置中心集成
    ├── anticipa-web-spring-boot-starter       ← Web 容器线程池适配
    │       ↑
    │       └── anticipa-dashboard-dev-starter ← 嵌入式 Dashboard 启动器
    └── anticipa-agent                         ← AI Agent 模块（依赖 anticipa-core）

anticipa-dashboard-dev                 ← 独立 Dashboard 管理应用
anticipa-example                       ← 使用示例
```

---

### 1. anticipa-core — 核心引擎（必须）

动态线程池的核心实现，不依赖 Spring，是整个框架的基石。

**职责：**
- 提供 `AnticipaExecutor`（扩展 `ThreadPoolExecutor`），支持线程池唯一标识、拒绝计数追踪、优雅关闭
- 提供 `AnticipaRegistry` 全局线程池注册表，管理所有动态线程池实例
- 实现 `ResizableCapacityLinkedBlockingQueue` 支持动态调整队列容量
- 实现 `ThreadPoolMonitor` 定时采集线程池运行指标（默认每 10 秒）
- 实现 `ThreadPoolAlarmChecker` 线程池告警检查与 `NotifierDispatcher` 通知分发
- 定义 `ThreadPoolExecutorProperties` 和 `BootstrapConfigProperties` 配置模型

**关键类：**

| 类名 | 说明 |
| --- | --- |
| `AnticipaExecutor` | 动态线程池执行器，扩展 ThreadPoolExecutor |
| `AnticipaRegistry` | 全局线程池注册表 |
| `ResizableCapacityLinkedBlockingQueue` | 可动态调整容量的阻塞队列 |
| `ThreadPoolMonitor` | 线程池运行时指标采集器 |
| `ThreadPoolAlarmChecker` | 线程池告警检查器 |
| `NotifierDispatcher` | 告警通知分发器 |
| `BootstrapConfigProperties` | 启动配置属性（支持 Nacos/Apollo/本地） |

---

### 2. anticipa-spring-base — Spring 集成基础（必须）

提供 Spring 集成的基础设施，实现注解驱动的线程池自动装配。

**职责：**
- 定义 `@DynamicThreadPool` 注解，用于标记需要动态管理的线程池 Bean
- 定义 `@EnableAnticipa` 注解，一键开启框架功能
- 实现 `AnticipaBeanPostProcessor`，扫描带有 `@DynamicThreadPool` 注解的 Bean，从配置中心读取配置并覆盖本地参数，注册到全局注册表
- 提供 `ApplicationContextHolder` 和 `SpringPropertiesLoader` 等基础工具

**依赖模块：** `anticipa-core`

**关键类：**

| 类名 | 说明 |
| --- | --- |
| `@DynamicThreadPool` | 动态线程池标记注解 |
| `@EnableAnticipa` | 框架启用注解 |
| `AnticipaBeanPostProcessor` | Bean 后置处理器，自动发现并注册线程池 |
| `AnticipaBaseConfiguration` | Spring Bean 配置类 |
| `ApplicationContextHolder` | 应用上下文静态访问器 |

---

### 3. anticipa-common-spring-boot-starter — 公共启动器（必须）

基于 Spring Boot 自动装配的公共模块，提供配置刷新监听机制。

**职责：**
- 实现 `CommonAutoConfiguration` 自动装配（条件：`onethread.enable=true`）
- 绑定 `BootstrapConfigProperties` 配置属性到 Spring Environment
- 实现 `DynamicThreadPoolRefreshListener` 监听配置变更事件，动态刷新线程池参数
- 提供 `AbstractDynamicThreadPoolRefresher` 抽象刷新器基类，供各配置中心 Starter 继承
- 显示 Anticipa Banner

**依赖模块：** `anticipa-spring-base`

**关键类：**

| 类名 | 说明 |
| --- | --- |
| `CommonAutoConfiguration` | 公共自动装配配置 |
| `DynamicThreadPoolRefreshListener` | 配置变更监听器 |
| `AbstractDynamicThreadPoolRefresher` | 抽象刷新器基类 |
| `ThreadPoolConfigUpdateEvent` | 线程池配置更新事件 |
| `AnticipaBannerHandler` | Banner 处理器 |

---

### 4. anticipa-nacos-spring-boot-starter — Nacos 配置中心集成（按需引入）

集成 Alibaba Nacos 配置中心，实现线程池配置的远程管理和动态刷新。

**职责：**
- 实现 `NacosCloudRefresherHandler`，继承 `AbstractDynamicThreadPoolRefresher`
- 监听 Nacos 配置变更事件，将变更应用到对应线程池

**依赖模块：** `anticipa-common-spring-boot-starter`

**何时引入：** 使用 Nacos 作为配置中心时引入。若使用其他配置中心（如 Apollo），则无需引入此模块。

---

### 5. anticipa-web-spring-boot-starter — Web 容器线程池适配（按需引入）

适配 Web 容器（Tomcat / Jetty / Undertow）内置线程池，使其也能被动态管理和监控。

**职责：**
- 条件化适配不同 Web 容器（`TomcatWebThreadPoolService`、`JettyWebThreadPoolService`）
- 提供 `WebThreadPoolService` 统一接口，封装 Web 线程池的查询与配置操作
- 实现 `WebThreadPoolRefreshListener` 监听 Web 线程池配置变更

**依赖模块：** `anticipa-common-spring-boot-starter`

**关键类：**

| 类名 | 说明 |
| --- | --- |
| `WebThreadPoolService` | Web 线程池服务接口 |
| `AbstractWebThreadPoolService` | Web 线程池服务抽象基类 |
| `TomcatWebThreadPoolService` | Tomcat 线程池适配器 |
| `JettyWebThreadPoolService` | Jetty 线程池适配器 |
| `WebThreadPoolRefreshListener` | Web 线程池配置变更监听器 |

**何时引入：** 需要管理 Web 容器（Tomcat/Jetty/Undertow）内置线程池时引入。

---

### 6. anticipa-dashboard-dev-starter — 嵌入式 Dashboard 启动器（按需引入）

为用户应用提供嵌入式的 Dashboard REST API，无需独立部署即可查询和调整线程池。

**职责：**
- 注册 `DynamicThreadPoolController` 和 `WebThreadPoolController` REST 接口
- 提供 `DynamicThreadPoolService` / `DynamicThreadPoolOperator` 线程池查询和操作服务
- 提供 `AdjustHistoryStore` 调整历史记录存储

**依赖模块：** `anticipa-web-spring-boot-starter`

**何时引入：** 需要在应用内嵌入线程池管理 API 时引入。

---

### 7. anticipa-agent — AI 智能代理（按需引入）

基于大模型（LLM）的智能 Agent 模块，提供 AI 驱动的线程池运维能力。

**职责：**
- 实现 `AgentLoop` Agent 主循环：调用 LLM → 解析工具调用 → 安全评估 → 执行 → 审计
- 提供 `ToolRegistry` 工具注册表和 `ToolDefinition` 工具定义
- 实现 `SafetyGuard` 安全守卫，评估 Agent 操作的安全性
- 实现 `ApprovalService` 审批服务，高危操作需人工审批
- 实现 `AuditStore` 操作审计存储
- 提供 `AIClient` / `OpenAIClient` 大模型客户端

**依赖模块：** `anticipa-core`

**关键类：**

| 类名 | 说明 |
| --- | --- |
| `AgentLoop` | Agent 主循环 |
| `ToolRegistry` | 工具注册表 |
| `AIClient` / `OpenAIClient` | 大模型客户端接口与实现 |
| `SafetyGuard` | 安全评估守卫 |
| `ApprovalService` | 审批服务 |
| `AuditStore` | 操作审计存储 |
| `SessionManager` | 会话管理 |

**何时引入：** 需要 AI 智能调优、智能运维建议能力时引入（需配置 `anticipa.agent.enabled=true`）。

---

### 8. anticipa-dashboard-dev — 独立 Dashboard 管理应用（独立部署）

完整的线程池可视化管理后端应用，可独立部署运行。

**职责：**
- 提供项目管理、线程池实例管理、Web 线程池管理、用户管理等完整 REST API
- 集成 Sa-Token 实现用户认证与登录过滤
- 集成 Grafana 数据源接口
- 集成 Agent 模块提供 AI 运维能力
- 全局异常处理与统一响应格式

**依赖模块：** `anticipa-core`、`anticipa-dashboard-dev-starter`、`anticipa-agent`

**说明：** 这是一个独立的 Spring Boot 应用（包含 `main` 方法），与用户应用分开部署。

---

### 9. anticipa-example — 使用示例（仅供参考）

展示如何使用 Anticipa 框架的示例项目。

**包含示例：**
- `nacos-cloud-example`：完整的 Nacos 配置中心集成示例，演示 `@EnableAnticipa` + `@DynamicThreadPool` 的标准用法

**说明：** 仅作为参考，不需要引入到用户项目中。

---

## 快速接入指南

### 最小接入（仅动态线程池 + Nacos）

```xml
<dependency>
    <groupId>com.baomihuahua</groupId>
    <artifactId>anticipa-nacos-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

此依赖会自动传递引入 `anticipa-common-spring-boot-starter` → `anticipa-spring-base` → `anticipa-core` 整条链路。

```java
@EnableAnticipa
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

```java
@Bean
@DynamicThreadPool
public ThreadPoolExecutor myThreadPool() {
    return new AnticipaExecutor(/* 参数 */);
}
```

### 完整接入（动态线程池 + Web 容器管理 + Dashboard + AI Agent）

```xml
<!-- Nacos 配置中心集成 -->
<dependency>
    <groupId>com.baomihuahua</groupId>
    <artifactId>anticipa-nacos-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Web 容器线程池管理 -->
<dependency>
    <groupId>com.baomihuahua</groupId>
    <artifactId>anticipa-web-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 嵌入式 Dashboard API -->
<dependency>
    <groupId>com.baomihuahua</groupId>
    <artifactId>anticipa-dashboard-dev-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- AI 智能调优 Agent -->
<dependency>
    <groupId>com.baomihuahua</groupId>
    <artifactId>anticipa-agent</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 模块引入决策表

| 模块 | 是否必须 | 引入条件 |
| --- | --- | --- |
| `anticipa-core` | 必须 | 自动传递引入，无需显式声明 |
| `anticipa-spring-base` | 必须 | 自动传递引入，无需显式声明 |
| `anticipa-common-spring-boot-starter` | 必须 | 自动传递引入，无需显式声明 |
| `anticipa-nacos-spring-boot-starter` | 按需 | 使用 Nacos 配置中心时引入 |
| `anticipa-web-spring-boot-starter` | 按需 | 需要管理 Web 容器线程池时引入 |
| `anticipa-dashboard-dev-starter` | 按需 | 需要嵌入式 Dashboard API 时引入 |
| `anticipa-agent` | 按需 | 需要 AI 智能调优时引入 |
| `anticipa-dashboard-dev` | 独立部署 | 作为独立管理应用部署 |
| `anticipa-example` | 不需要 | 仅作为参考示例 |

## License

详见 [LICENSE](LICENSE) 文件。

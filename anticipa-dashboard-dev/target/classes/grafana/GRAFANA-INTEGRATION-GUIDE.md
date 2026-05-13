# Grafana 监控集成指南

本文档介绍如何为 Anticipa 动态线程池框架部署 Prometheus + Grafana 监控体系，并完成与管控平台的集成。

---

## 整体架构

```
┌──────────────┐   scrape    ┌──────────────┐   query    ┌──────────────┐   iframe    ┌──────────────┐
│ 线程池应用实例 │ ────────── │  Prometheus   │ ───────── │    Grafana    │ ────────── │  管控平台前端  │
│ :port         │  /actuator  │   :9090       │   PromQL  │   :3000       │   嵌入     │  grafana 页面 │
│ /prometheus   │  每 15s     │               │           │  Dashboard   │           │              │
└──────────────┘             └──────────────┘           └──────────────┘           └──────────────┘
```

**数据链路**: 线程池运行时指标 → Micrometer → `/actuator/prometheus` → Prometheus 采集 → Grafana 可视化 → 管控平台 iframe 嵌入

---

## 一、前置条件

- Docker 已安装
- Anticipa 线程池应用已启动，且配置了 Prometheus 指标导出
- `anticipa-core` 中的 `ThreadPoolMetricsBinder` 已自动注册（需 `MeterRegistry` Bean 存在）

### 1.1 应用端开启 Prometheus 指标导出

在接入 Anticipa 的业务应用中，确保以下依赖和配置：

**Maven 依赖**（在业务应用 pom.xml 中添加）：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**application.yaml 配置**：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info
  metrics:
    prometheus:
      metrics:
        export:
          enabled: true
  endpoint:
    prometheus:
      enabled: true
```

**验证指标导出**：启动应用后访问 `http://localhost:{port}/actuator/prometheus`，应能看到以下格式的指标：

```
dynamic_thread_pool_active_count{application_name="your-app",dynamic_thread_pool_id="your-pool"} 5.0
dynamic_thread_pool_core_pool_size{application_name="your-app",dynamic_thread_pool_id="your-pool"} 10.0
dynamic_thread_pool_queue_size{application_name="your-app",dynamic_thread_pool_id="your-pool"} 3.0
...
```

### 1.2 暴露的指标清单

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `dynamic_thread_pool_core_pool_size` | Gauge | 核心线程数 |
| `dynamic_thread_pool_maximum_pool_size` | Gauge | 最大线程数 |
| `dynamic_thread_pool_pool_size` | Gauge | 当前线程数 |
| `dynamic_thread_pool_active_count` | Gauge | 活跃线程数 |
| `dynamic_thread_pool_largest_pool_size` | Gauge | 历史最大线程数 |
| `dynamic_thread_pool_queue_size` | Gauge | 当前队列大小 |
| `dynamic_thread_pool_queue_capacity` | Gauge | 队列总容量 |
| `dynamic_thread_pool_queue_remaining_capacity` | Gauge | 队列剩余容量 |
| `dynamic_thread_pool_completed_task_count` | Gauge | 累计完成任务数 |
| `dynamic_thread_pool_reject_count` | Gauge | 拒绝任务数 |

所有指标均带标签：
- `application_name` — Spring 应用名称（`spring.application.name`）
- `dynamic_thread_pool_id` — 线程池唯一标识

---

## 二、部署 Prometheus

### 2.1 创建 Docker 网络

```bash
docker network create monitoring
```

### 2.2 创建 Prometheus 配置文件

创建 `prometheus.yml`：

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'anticipa-thread-pool'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
    static_configs:
      # 替换为实际应用地址，多个实例写多行
      - targets: ['host.docker.internal:8080', 'host.docker.internal:8081']
        labels:
          cluster: 'dev'
```

> **注意**: 如果应用和 Docker 在同一台机器上，使用 `host.docker.internal` 替代 `localhost` 以让容器内访问宿主机端口。

### 2.3 启动 Prometheus

```bash
docker run -d \
  --name prometheus \
  --network monitoring \
  -p 9090:9090 \
  -v /path/to/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus:v2.47.0
```

**验证**: 访问 http://localhost:9090/targets ，确认目标状态为 UP。

---

## 三、部署 Grafana

### 3.1 启动 Grafana

```bash
docker network connect monitoring prometheus

docker run -d \
  --name grafana \
  --network monitoring \
  -p 3000:3000 \
  grafana/grafana:9.0.5
```

**登录**: 访问 http://localhost:3000 ，默认账号 `admin/admin`。首次登录后可跳过密码修改。

### 3.2 添加 Prometheus 数据源

1. 左侧菜单 → Configuration → Data Sources → Add data source
2. 选择 **Prometheus**
3. HTTP URL 填写：`http://prometheus:9090`（通过 Docker 内部网络通信）
4. 点击 **Save & test**，出现绿色提示即成功

### 3.3 导入 Dashboard 模板

1. 左侧菜单 → Dashboards → Browse → Import
2. 上传 `anticipa-threadpool-dashboard.json` 文件
    - 文件位置：`anticipa-dashboard-dev/src/main/resources/grafana/anticipa-threadpool-dashboard.json`
3. 选择 Prometheus 数据源
4. 点击 **Import**

导入后即可看到 **Anticipa 线程池健康度仪表盘**，包含：
- 核心线程数 vs 当前线程数时序图
- 线程使用率仪表盘
- 队列使用情况时序图
- 队列使用率仪表盘
- 完成任务数增速
- 拒绝数趋势
- 全局概览表

---

## 四、与管控平台集成

### 4.1 配置 Grafana URL

在 `anticipa-dashboard-dev` 的 `application.yaml` 中配置：

```yaml
anticipa:
  grafana:
    url: http://localhost:3000/d/anticipa-threadpool?orgId=1&from=now-6h&to=now&timezone=browser&var-application_name=your-app&var-dynamic_thread_pool_id=your-pool&refresh=5s&theme=light&kiosk=true
```

**URL 参数说明**：

| 参数 | 说明 | 示例 |
|------|------|------|
| `var-application_name` | 预选应用名称 | `nacos-cloud-example` |
| `var-dynamic_thread_pool_id` | 预选线程池 ID | `onethread-consumer` |
| `refresh` | 自动刷新间隔 | `5s` / `10s` / `30s` |
| `theme=light` | 浅色主题，与管控平台 UI 一致 | — |
| `kiosk=true` | 隐藏 Grafana 顶栏侧栏，全屏嵌入 | — |
| `from` / `to` | 默认时间范围 | `now-6h` / `now` |

### 4.2 公网部署

如果将 Grafana 部署到公网，将 URL 中的 `localhost:3000` 替换为公网域名：

```yaml
anticipa:
  grafana:
    url: https://grafana.yourdomain.com/d/anticipa-threadpool?orgId=1&from=now-6h&to=now&theme=light&kiosk=true&refresh=5s
```

### 4.3 前端展示

配置完成后，在管控平台左侧菜单点击 **Grafana 监控**，页面将自动通过 iframe 嵌入 Grafana 面板。

- **已配置**: 页面直接展示 Grafana iframe 全屏仪表盘
- **未配置**: 页面展示引导步骤，提示配置 `anticipa.grafana.url`

---

## 五、Dashboard 面板说明

### 面板布局

```
┌─────────────────────────┬──────────┬──────────┐
│  核心线程数 vs 当前线程数  │ 线程使用率 │ 历史最大  │
│  (时序图)                │ (仪表盘)  │ 线程数    │
├─────────────────────────┼──────────┼──────────┤
│  队列使用情况             │ 队列使用率 │  拒绝数   │
│  (时序图)                │ (仪表盘)  │  (统计)   │
├─────────────────────────┼──────────┤          │
│  完成任务数（增速）       │ 累计完成  │          │
│  (时序图)                │ (统计)    │          │
├─────────────────────────┼──────────┤          │
│  拒绝数趋势               │ 全局概览  │          │
│  (柱状图)                │ (表格)    │          │
└─────────────────────────┴──────────┴──────────┘
```

### 变量筛选

Dashboard 顶部提供两个下拉筛选器：
- **应用名称** (`application_name`): 从 Prometheus 标签自动发现
- **线程池 ID** (`dynamic_thread_pool_id`): 根据选中的应用名联动筛选

集群部署时，同一个 `application_name` + `dynamic_thread_pool_id` 组合可能对应多个实例，图表会自动展示所有实例的聚合数据。

---

## 六、常见问题

### Q1: `/actuator/prometheus` 无线程池指标

确认 `spring-boot-starter-actuator` 和 `micrometer-registry-prometheus` 依赖已添加，且 `management.endpoints.web.exposure.include` 包含 `prometheus`。`ThreadPoolMetricsBinder` 仅在 `MeterRegistry` Bean 存在时自动注册。

### Q2: Prometheus targets 显示 DOWN

- 检查应用是否可达：在 Prometheus 容器内 `curl http://target:port/actuator/prometheus`
- Docker 网络配置：确保 Prometheus 和应用在同一网络，或使用 `host.docker.internal`
- 防火墙规则：确认应用端口已开放

### Q3: Grafana iframe 嵌入空白

- 确认 Grafana 配置允许 iframe 嵌入：在 `grafana.ini` 中设置 `allow_embedding = true`
- 检查浏览器控制台是否有 X-Frame-Options 或 CSP 错误
- URL 参数中确保包含 `kiosk=true` 和 `theme=light`

### Q4: 多实例部署时如何监控

在 Prometheus 的 `static_configs` 中配置多个 targets 即可。同一 `application_name` + `dynamic_thread_pool_id` 的多实例数据会自动聚合展示。

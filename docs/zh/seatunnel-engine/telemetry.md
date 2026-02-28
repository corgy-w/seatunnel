---

sidebar_position: 13
--------------------

# Telemetry

SeaTunnel Engine Telemetry 通过 Prometheus 指标（Metrics/OpenMetrics）与监控平台（如 Prometheus、Grafana）集成，用于提升集群的可观测性与告警能力。

## 启用 Telemetry

在 `seatunnel.yaml` 中开启指标导出：

```yaml
seatunnel:
  engine:
    telemetry:
      metric:
        enabled: true
```

说明：
- 指标导出默认开启；如需关闭，可设置 `seatunnel.engine.telemetry.metric.enabled: false`。

## 指标端点

- Prometheus 文本：`http://{instanceHost}:5801/hazelcast/rest/instance/metrics`
- OpenMetrics：`http://{instanceHost}:5801/hazelcast/rest/instance/openmetrics`

对照示例文本（用于快速比对与排障）：
- Prometheus：[metrics.txt](./telemetry/metrics.txt)
- OpenMetrics：[openmetrics.txt](./telemetry/openmetrics.txt)

两种端点暴露的 **指标集合一致**（同一套 time series），差异主要在输出格式：
- `metrics`：Prometheus Text Exposition Format（Prometheus 默认抓取格式）
- `openmetrics`：OpenMetrics 格式（包含 `# EOF`；Counter 的 `# TYPE` 使用 base name，但样本仍以 `*_total` 暴露）

完整指标定义（推荐阅读）：[metrics.md](./telemetry/metrics.md)。

## 标签与口径说明（避免误解）

### Labels 来源

- **Prometheus 抓取附加标签**：如 `instance`、`job` 等来自 Prometheus 配置，不属于指标本身定义。
- **SeaTunnel Engine 自定义指标标签**：通常带 `cluster`（值为 `hazelcast.cluster-name`），部分指标还带 `address`、`type`。
- **JVM/Process 指标标签**：来自 Prometheus Java Client `DefaultExports`（HotSpot 指标），一般不带 `cluster/address`，但可能带 `pool/area/gc/state/...` 等 JVM 维度标签。

其中 `address` 表示 **Hazelcast member 地址（host:port）**，在 K8s 中通常是 Pod IP + 端口；如果出现 `127.0.0.1:*` 且多节点都相同，一般意味着网络/发布地址配置不正确或 Prometheus 抓取目标配置不合理。

### Counter 与 `_total`

Prometheus 中 Counter 通常以 `*_total` 的指标名暴露；请以实际抓取到的指标名为准配置 Grafana 查询与告警规则。

### `*_created` 指标

`*_created` 一般表示“该时间序列创建时间”（epoch seconds），不是业务含义的“已创建/已分配”数量。

## Prometheus 抓取配置（强烈建议抓取每个节点）

建议 Prometheus 抓取每个 SeaTunnel Engine 节点的 `/metrics`，以避免单点缺失，并能看到 `address` 维度的节点数据。

示例（两节点）：

```yaml
scrape_configs:
  - job_name: 'seatunnel'
    scrape_interval: 15s
    metrics_path: /hazelcast/rest/instance/metrics
    static_configs:
      - targets:
          - 'localhost:5801'
          - 'localhost:5802'
```

### K8s 抓取建议（生产环境常用）

- 建议 Prometheus **直接抓取每个 SeaTunnel Engine Pod**（而不是抓取普通 Service 的负载均衡入口），避免多节点指标被“打散/抖动”。
- 建议在 Prometheus 中保留 `namespace`/`pod` 等标签，并将 `instance` 维度明确为 `podIP:5801`（或使用 `pod` + `address` 组合做定位）。
- 如果抓取到的 `address` 长期为 `127.0.0.1:*`，请优先检查 Hazelcast 的网络/发布地址配置，以及 Pod 网络模式是否正确。

### 常用 PromQL（多节点抓取时避免重复计数）

- 集群节点数（cluster 级指标，建议 `max` 去重）：`max(node_count{instance=~"$instance"})`
- Prometheus 实际抓取到的节点数（每个 target 上报 1/0）：`sum(node_state{instance=~"$instance"})`
- 各状态作业数（只在 master 输出，建议 `max by(type)` 稳定展示）：`max by (type) (job_count{instance=~"$instance"})`
- 集群时间（epoch ms，cluster 级指标）：`max(cluster_time{instance=~"$instance"})`
- 进程启动时间（epoch seconds）：`process_start_time_seconds{instance=~"$instance"}`
- 进程 CPU 使用率估算（百分比）：`rate(process_cpu_seconds_total{instance=~"$instance"}[1m]) * 100`

### 告警建议（示例，阈值请按环境调整）

- 抓取节点数小于集群节点数（可能有节点宕机或 Prometheus 未抓到该节点）：
  - `sum(node_state{instance=~"$instance"}) < max(node_count{instance=~"$instance"})`
- 协调器线程池出现拒绝（持续为非零说明线程池/队列压力过大）：
  - `rate(job_thread_pool_rejection_total{instance=~"$instance"}[5m]) > 0`
- JVM 堆使用率过高（可能触发频繁 GC 或 OOM 风险）：
  - `100 * (jvm_memory_bytes_used{area="heap",instance=~"$instance"} / jvm_memory_bytes_max{area="heap",instance=~"$instance"}) > 80`

## Grafana Dashboard

导入 Grafana Dashboard JSON：
- Dashboard：`Seatunnel Cluster`
- JSON 文件：[grafana-dashboard.json](./telemetry/grafana-dashboard.json)

Dashboard 变量：
- `instance`：来自 Prometheus 抓取标签 `instance`，支持多选/全选；多节点抓取时可用该变量筛选展示范围。

## 排障与 FAQ

### 1) “集群节点数是 2，但抓取节点数是 1”

常见原因是 Prometheus 只配置了一个 target（例如只抓 `:5801`）。请在 `static_configs.targets` 里把每个节点都加入抓取列表。

### 2) 看不到 `job_thread_pool_queueTaskCount` 或 `job_thread_pool_rejection_total`

请确认：
- Telemetry 已开启（`enabled: true`）
- 访问的是 `/hazelcast/rest/instance/metrics`（不是别的端点）
- 使用的版本已包含这些指标（指标清单以 [metrics.md](./telemetry/metrics.md) 为准）

### 3) JVM Memory Pools 是什么数据

`jvm_memory_pool_*` 是 JVM 内存池（如 Eden/Old/Metaspace 等）的指标，属于 JVM/Process 类别，并非“操作系统/服务器内存池”指标。

## 安全建议

指标中包含集群拓扑与节点地址等信息。建议在网络与访问控制层面限制 `/hazelcast/rest/instance/metrics` 与 `/openmetrics` 的访问范围。

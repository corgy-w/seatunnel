# SeaTunnel Engine Telemetry Metrics

本文档面向使用方（运维/开发/客户 SRE），用于理解与使用 SeaTunnel Engine 暴露的 Prometheus 指标（Metrics/OpenMetrics），可直接用于 Grafana 查询与告警规则配置。

## 端点

- Prometheus 文本：`http://{instanceHost}:5801/hazelcast/rest/instance/metrics`
- OpenMetrics：`http://{instanceHost}:5801/hazelcast/rest/instance/openmetrics`

## Labels（标签）约定

- Prometheus 抓取时会额外附加 `instance`、`job` 等标签，它们来自 Prometheus 配置，不属于指标本身定义。
- SeaTunnel Engine 自定义指标通常带 `cluster`（Hazelcast 集群名），部分带 `address`、`type`。
- JVM/Process 指标通常不带 `cluster/address`，但会带 `pool/area/gc/state/vendor/version/...` 等维度标签。

其中 `address` 为 Hazelcast member 地址（host:port）。在 K8s 中一般对应 Pod IP；建议 Prometheus 直接抓取 Pod（而不是抓取普通 Service 的负载均衡入口），以避免指标“抖动”与节点维度缺失。

## 指标类型（Prometheus Type）

- `gauge`：瞬时值（可上可下），例如“当前线程数”、“队列长度”。
- `counter`：只增不减的累计值（重启会重置），通常在 Prometheus 中以 `*_total` 暴露；常用 `rate()`/`increase()` 计算速率或区间增量。
- `summary`：分位数/汇总类指标；本系统目前使用其 `*_count` / `*_sum` 子指标（例如 GC 次数与总耗时）。

## 常见口径说明（先看这里）

### 1) `Pool Size` vs `Max Pool Size` 有什么区别？

以线程池为例（包括 `job_thread_pool_*` 与 `hazelcast_executor_*`）：

- **Pool Size**：线程池当前实际持有的线程数（会随着负载、回收策略变化）。
- **Max Pool Size**：线程池允许增长的上限（配置/实现决定的最大值）；当并发与队列策略允许时，Pool Size 才会逐步增长到接近 Max。

因此：
- Pool Size 反映“当前实际资源占用”
- Max Pool Size 反映“理论上限/配置上限”

### 2) 多节点抓取时，哪些指标需要“去重”？

一些“集群级”指标（例如 `node_count`、`cluster_time`）在每个被抓取节点都会暴露同样的值。多节点抓取时建议用 `max()` 聚合避免重复计数。

示例：
- 集群节点数：`max(node_count{instance=~"$instance"})`
- 抓取到的节点数（每个目标上报 1/0）：`sum(node_state{instance=~"$instance"})`

### 3) `*_created` 指标代表什么？

`*_created` 一般表示该时间序列创建时间戳（epoch seconds）。例如 `jvm_memory_pool_allocated_bytes_created` 是 `jvm_memory_pool_allocated_bytes_total` 的“创建时间”，不是“分配字节数”。

## 指标清单（当前系统：65 个 metric name）

字段说明：
- **Metric**：Prometheus 指标名（以最终 scrape 输出为准）
- **Type**：`gauge` / `counter` / `summary`
- **Labels**：指标自带标签键（不含 Prometheus 抓取附加标签）
- **单位**：值的物理单位/含义

### A. SeaTunnel Engine（自定义指标）

#### A1. Cluster

|     Metric     |  Type   |                Labels                 |    单位     |                      说明                       |
|----------------|---------|---------------------------------------|-----------|-----------------------------------------------|
| `cluster_info` | `gauge` | `cluster`,`hazelcastVersion`,`master` | bool(0/1) | 集群信息探针；正常为 1，获取失败为 0。                         |
| `cluster_time` | `gauge` | `cluster`,`hazelcastVersion`          | ms(epoch) | 集群时间（epoch 毫秒，Hazelcast cluster clock 的当前时间）。 |
| `node_count`   | `gauge` | `cluster`                             | count     | 集群节点数量；多节点抓取时建议 `max(node_count{...})`。       |

#### A2. Node

|    Metric    |  Type   |       Labels        |    单位     |              说明              |
|--------------|---------|---------------------|-----------|------------------------------|
| `node_state` | `gauge` | `cluster`,`address` | bool(0/1) | 节点存活探针；每个被抓取节点上报自身状态（通常为 1）。 |

#### A3. Job

|   Metric    |  Type   |      Labels      |  单位   |                                            说明                                             |
|-------------|---------|------------------|-------|-------------------------------------------------------------------------------------------|
| `job_count` | `gauge` | `cluster`,`type` | count | 各状态作业数量；`type` ∈ `created/scheduled/running/failing/failed/cancelling/canceled/finished`。 |

#### A4. Coordinator Job Thread Pool

|                Metric                 |   Type    |       Labels        |   单位    |              说明               |
|---------------------------------------|-----------|---------------------|---------|-------------------------------|
| `job_thread_pool_activeCount`         | `gauge`   | `cluster`,`address` | threads | 线程池当前活动线程数。                   |
| `job_thread_pool_corePoolSize`        | `gauge`   | `cluster`,`address` | threads | 线程池 corePoolSize（核心线程数）。      |
| `job_thread_pool_maximumPoolSize`     | `gauge`   | `cluster`,`address` | threads | 线程池 maximumPoolSize（最大线程数上限）。 |
| `job_thread_pool_poolSize`            | `gauge`   | `cluster`,`address` | threads | 线程池 poolSize（当前线程数）。          |
| `job_thread_pool_queueTaskCount`      | `gauge`   | `cluster`,`address` | tasks   | 线程池队列中当前等待的任务数。               |
| `job_thread_pool_completedTask_total` | `counter` | `cluster`,`address` | tasks   | 线程池已完成任务累计数（Counter）。         |
| `job_thread_pool_task_total`          | `counter` | `cluster`,`address` | tasks   | 线程池任务累计数（Counter）。            |
| `job_thread_pool_rejection_total`     | `counter` | `cluster`,`address` | tasks   | 线程池拒绝执行任务累计数（Counter）。        |

#### A5. Hazelcast Executor（节点维度）

|                   Metric                    |  Type   |           Labels           |    单位     |                   说明                   |
|---------------------------------------------|---------|----------------------------|-----------|----------------------------------------|
| `hazelcast_executor_executedCount`          | `gauge` | `cluster`,`address`,`type` | count     | Executor 执行次数（累计值，Hazelcast MBean 提供）。 |
| `hazelcast_executor_isShutdown`             | `gauge` | `cluster`,`address`,`type` | bool(0/1) | Executor 是否 shutdown。                  |
| `hazelcast_executor_isTerminated`           | `gauge` | `cluster`,`address`,`type` | bool(0/1) | Executor 是否 terminated。                |
| `hazelcast_executor_maxPoolSize`            | `gauge` | `cluster`,`address`,`type` | threads   | Executor 最大线程数上限。                      |
| `hazelcast_executor_poolSize`               | `gauge` | `cluster`,`address`,`type` | threads   | Executor 当前线程数。                        |
| `hazelcast_executor_queueRemainingCapacity` | `gauge` | `cluster`,`address`,`type` | count     | Executor 队列剩余容量。                       |
| `hazelcast_executor_queueSize`              | `gauge` | `cluster`,`address`,`type` | count     | Executor 队列当前大小。                       |

#### A6. Hazelcast Partition（节点维度）

|                 Metric                  |  Type   |       Labels        |    单位     |                  说明                  |
|-----------------------------------------|---------|---------------------|-----------|--------------------------------------|
| `hazelcast_partition_partitionCount`    | `gauge` | `cluster`,`address` | count     | 集群分区总数（所有节点值相同；多节点抓取建议用 `max()` 去重）。 |
| `hazelcast_partition_activePartition`   | `gauge` | `cluster`,`address` | count     | 当前节点（local member）分配到的分区数量（不是全局总数）。  |
| `hazelcast_partition_isClusterSafe`     | `gauge` | `cluster`,`address` | bool(0/1) | 分区是否 cluster safe。                   |
| `hazelcast_partition_isLocalMemberSafe` | `gauge` | `cluster`,`address` | bool(0/1) | 本地成员是否 safe。                         |

#### A7. Slot & Resource（节点维度）

|          Metric          |   Type    |            Labels            |     单位     |                                             说明                                              |
|--------------------------|-----------|------------------------------|------------|---------------------------------------------------------------------------------------------|
| `slot_total`             | `gauge`   | `cluster`,`address`          | count      | 当前 worker 配置的 slot 容量（`slotNum`）。                                                           |
| `slot_assigned`          | `gauge`   | `cluster`,`address`          | count      | 已分配 slot 数。                                                                                 |
| `slot_unassigned`        | `gauge`   | `cluster`,`address`          | count      | 空闲 slot 容量（`slot_total - slot_assigned`）。                                                   |
| `slot_utilization_ratio` | `gauge`   | `cluster`,`address`          | ratio(0~1) | slot 使用率（`assigned/slot_total`）；当 `slot_unassigned=0` 时为 1（Grafana 显示 100%），表示该节点没有空闲 slot。 |
| `slot_request_total`     | `counter` | `cluster`,`address`,`result` | count      | slot 请求累计次数；`result` ∈ `success/failure`。                                                   |
| `slot_release_total`     | `counter` | `cluster`,`address`          | count      | slot 释放累计次数。                                                                                |

### B. JVM & Process（运行时指标）

|                    Metric                    |   Type    |            Labels            |    单位     |                           说明                            |
|----------------------------------------------|-----------|------------------------------|-----------|---------------------------------------------------------|
| `process_cpu_seconds_total`                  | `counter` | -                            | seconds   | 进程累计 CPU 时间（用户态+内核态）。                                   |
| `process_start_time_seconds`                 | `gauge`   | -                            | s(epoch)  | 进程启动时间（Unix 秒）。                                         |
| `process_open_fds`                           | `gauge`   | -                            | count     | 已打开文件描述符数。                                              |
| `process_max_fds`                            | `gauge`   | -                            | count     | 最大允许文件描述符数。                                             |
| `jvm_info`                                   | `gauge`   | `runtime`,`vendor`,`version` | bool(0/1) | JVM 版本信息探针；通常为 1。                                       |
| `jvm_classes_currently_loaded`               | `gauge`   | -                            | count     | 当前 JVM 已加载类数量。                                          |
| `jvm_classes_loaded_total`                   | `counter` | -                            | count     | JVM 启动以来累计加载类数量。                                        |
| `jvm_classes_unloaded_total`                 | `counter` | -                            | count     | JVM 启动以来累计卸载类数量。                                        |
| `jvm_gc_collection_seconds_count`            | `summary` | `gc`                         | count     | GC 次数（`jvm_gc_collection_seconds` 的 `count` 子指标）。       |
| `jvm_gc_collection_seconds_sum`              | `summary` | `gc`                         | seconds   | GC 总耗时（秒，`jvm_gc_collection_seconds` 的 `sum` 子指标）。      |
| `jvm_threads_current`                        | `gauge`   | -                            | threads   | 当前线程数。                                                  |
| `jvm_threads_daemon`                         | `gauge`   | -                            | threads   | Daemon 线程数。                                             |
| `jvm_threads_peak`                           | `gauge`   | -                            | threads   | 峰值线程数。                                                  |
| `jvm_threads_started_total`                  | `counter` | -                            | threads   | JVM 启动以来启动过的线程总数（累计）。                                   |
| `jvm_threads_deadlocked`                     | `gauge`   | -                            | threads   | 死锁线程数（等待 object monitors 或 ownable synchronizers）。      |
| `jvm_threads_deadlocked_monitor`             | `gauge`   | -                            | threads   | 死锁线程数（仅等待 object monitors）。                             |
| `jvm_threads_state`                          | `gauge`   | `state`                      | threads   | 按线程状态聚合的线程数。                                            |
| `jvm_memory_objects_pending_finalization`    | `gauge`   | -                            | count     | Finalizer 队列中等待的对象数量。                                   |
| `jvm_memory_bytes_used`                      | `gauge`   | `area`                       | bytes     | JVM 内存区域已用字节数；`area` ∈ `heap/nonheap`。                  |
| `jvm_memory_bytes_committed`                 | `gauge`   | `area`                       | bytes     | JVM 内存区域 committed 字节数。                                 |
| `jvm_memory_bytes_max`                       | `gauge`   | `area`                       | bytes     | JVM 内存区域 max 字节数。                                       |
| `jvm_memory_bytes_init`                      | `gauge`   | `area`                       | bytes     | JVM 内存区域 init 字节数。                                      |
| `jvm_memory_pool_bytes_used`                 | `gauge`   | `pool`                       | bytes     | JVM 内存池已用字节数。                                           |
| `jvm_memory_pool_bytes_committed`            | `gauge`   | `pool`                       | bytes     | JVM 内存池 committed 字节数。                                  |
| `jvm_memory_pool_bytes_max`                  | `gauge`   | `pool`                       | bytes     | JVM 内存池 max 字节数。                                        |
| `jvm_memory_pool_bytes_init`                 | `gauge`   | `pool`                       | bytes     | JVM 内存池 init 字节数。                                       |
| `jvm_memory_pool_collection_used_bytes`      | `gauge`   | `pool`                       | bytes     | JVM 内存池“上次 GC 后” used 字节数。                              |
| `jvm_memory_pool_collection_committed_bytes` | `gauge`   | `pool`                       | bytes     | JVM 内存池“上次 GC 后” committed 字节数。                         |
| `jvm_memory_pool_collection_max_bytes`       | `gauge`   | `pool`                       | bytes     | JVM 内存池“上次 GC 后” max 字节数。                               |
| `jvm_memory_pool_collection_init_bytes`      | `gauge`   | `pool`                       | bytes     | JVM 内存池“上次 GC 后” init 字节数。                              |
| `jvm_memory_pool_allocated_bytes_total`      | `counter` | `pool`                       | bytes     | JVM 内存池累计分配字节（Counter，仅在 GC 后更新）。                       |
| `jvm_memory_pool_allocated_bytes_created`    | `gauge`   | `pool`                       | s(epoch)  | `jvm_memory_pool_allocated_bytes_total` 的创建时间戳（Unix 秒）。 |
| `jvm_buffer_pool_used_bytes`                 | `gauge`   | `pool`                       | bytes     | JVM buffer pool 已用字节数；`pool` ∈ `direct/mapped`。         |
| `jvm_buffer_pool_capacity_bytes`             | `gauge`   | `pool`                       | bytes     | JVM buffer pool 容量字节数。                                  |
| `jvm_buffer_pool_used_buffers`               | `gauge`   | `pool`                       | count     | JVM buffer pool 已用 buffer 个数。                           |

## 常用 PromQL（可直接用于 Grafana/告警）

- 集群节点数：`max(node_count{instance=~"$instance"})`
- 抓取到的节点数：`sum(node_state{instance=~"$instance"})`
- 各状态作业数：`max by (type) (job_count{instance=~"$instance"})`
- 线程池队列长度：`job_thread_pool_queueTaskCount{instance=~"$instance"}`
- 线程池拒绝率（每秒）：`rate(job_thread_pool_rejection_total{instance=~"$instance"}[5m])`
- JVM 堆使用率（%）：`100 * (jvm_memory_bytes_used{area="heap",instance=~"$instance"} / jvm_memory_bytes_max{area="heap",instance=~"$instance"})`

## 运维自检：如何验证“文档与系统一致”

获取 endpoint 的指标名（去掉注释行与 labels），并确认输出为 65 行：

```bash
curl -sS http://localhost:5801/hazelcast/rest/instance/metrics \
  | awk 'NF && $1 !~ /^#/ {n=$1; sub(/\\{.*/,\"\",n); print n}' \
  | sort -u | wc -l
```

当前版本应输出 **65**。如果数量变化，说明版本升级带来指标变更，需要同步更新本文档与 Grafana Dashboard。

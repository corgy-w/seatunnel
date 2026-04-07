# Doris

> Doris 数据接收器

## 支持的 Doris 版本

- exactly-once 和 cdc 支持 `Doris version is >= 1.1.x`
- 数组数据类型支持 `Doris version is >= 1.2.x`
- Map 数据类型将在 `Doris version is 2.x` 中支持

## 引擎支持

> Spark<br/>
> Flink<br/>
> SeaTunnel Zeta<br/>

## 主要特性

- [x] [精确一次](../../concept/connector-v2-features.md)
- [x] [cdc](../../concept/connector-v2-features.md)

## 描述

用于将数据写入 Doris，同时支持流模式和批模式。
Doris Sink 连接器的内部实现基于 stream load 分批缓存并导入数据。

## Sink 选项

|               Name               |  Type   | Required |           Default            |                                                                       Description                                                                       |
|----------------------------------|---------|----------|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| fenodes                          | String  | Yes      | -                            | `Doris` 集群 fenodes 地址，格式为 `"fe_ip:fe_http_port, ..."`                                                                                                   |
| query-port                       | int     | No       | 9030                         | `Doris` Fenodes query_port                                                                                                                              |
| username                         | String  | Yes      | -                            | `Doris` 用户名                                                                                                                                             |
| password                         | String  | Yes      | -                            | `Doris` 密码                                                                                                                                              |
| database                         | String  | Yes      | -                            | `Doris` 数据库名，使用 `${database_name}` 表示上游数据库名                                                                                                             |
| table                            | String  | Yes      | -                            | `Doris` 表名，使用 `${table_name}` 表示上游表名                                                                                                                    |
| table.identifier                 | String  | Yes      | -                            | `Doris` 表标识，2.3.5 版本后将弃用，请改用 `database` 和 `table`                                                                                                       |
| sink.label-prefix                | String  | Yes      | -                            | stream load 导入使用的标签前缀。在 2pc 场景下，需要全局唯一性来保证 SeaTunnel 的 EOS 语义                                                                                           |
| sink.enable-2pc                  | bool    | No       | false                        | 是否启用两阶段提交（2pc），默认为 false。对于两阶段提交，请参考[这里](https://doris.apache.org/docs/dev/sql-manual/sql-reference/Data-Manipulation-Statements/Load/STREAM-LOAD)      |
| sink.enable-delete               | bool    | No       | -                            | 是否启用删除。该选项要求 Doris 表开启批量删除功能（0.15+ 版本默认开启），且仅支持 Unique 模型。更多说明请参考[这里](https://doris.apache.org/docs/dev/data-operate/update-delete/batch-delete-manual) |
| sink.check-interval              | int     | No       | 10000                        | 加载过程中检查异常的时间间隔                                                                                                                                          |
| sink.max-retries                 | int     | No       | 3                            | 向数据库写入记录失败时的最大重试次数                                                                                                                                      |
| sink.buffer-size                 | int     | No       | 256 * 1024                   | 用于缓存 stream load 数据的缓冲区大小                                                                                                                               |
| sink.buffer-count                | int     | No       | 3                            | 用于缓存 stream load 数据的缓冲区数量                                                                                                                               |
| doris.batch.size                 | int     | No       | 1024                         | 每次 HTTP 请求写入 Doris 的批量大小；当记录数达到该值或执行 checkpoint 时，缓存数据会被写入服务器                                                                                           |
| doris.request.connect.timeout.ms | int     | No       | 60000                        | stream load 和 2pc commit/abort 使用的 Doris FE HTTP 连接超时时间，单位为毫秒                                                                                           |
| needs_unsupported_type_casting   | boolean | No       | false                        | 是否启用不支持的类型转换，例如 Decimal64 到 Double                                                                                                                      |
| schema_save_mode                 | Enum    | no       | CREATE_SCHEMA_WHEN_NOT_EXIST | schema 保存模式，请参考下面的 `schema_save_mode`                                                                                                                   |
| data_save_mode                   | Enum    | no       | APPEND_DATA                  | 数据保存模式，请参考下面的 `data_save_mode`                                                                                                                          |
| save_mode_create_template        | string  | no       | see below                    | 见下文                                                                                                                                                     |
| custom_sql                       | String  | no       | -                            | 当 `data_save_mode` 选择 `CUSTOM_PROCESSING` 时，需要填写 `CUSTOM_SQL` 参数。该参数通常填写一条可执行 SQL，且会在同步任务启动前执行                                                          |
| doris.config                     | map     | yes      | -                            | 该选项用于支持自动生成 SQL 时的 `insert`、`delete`、`update` 等操作，以及相关格式配置                                                                                              |

### schema_save_mode[Enum]

在启动同步任务前，针对目标侧已有表结构选择不同的处理方式。  
选项说明：  
`RECREATE_SCHEMA`：表不存在时创建，表已存在时删除并重建  
`CREATE_SCHEMA_WHEN_NOT_EXIST`：表不存在时创建，表已存在时跳过  
`ERROR_WHEN_SCHEMA_NOT_EXIST`：表不存在时报错

### data_save_mode[Enum]

在启动同步任务前，针对目标侧已有数据选择不同的处理方式。  
选项说明：  
`DROP_DATA`：保留数据库结构并删除数据  
`APPEND_DATA`：保留数据库结构并保留数据  
`CUSTOM_PROCESSING`：用户自定义处理  
`ERROR_WHEN_DATA_EXISTS`：当存在数据时抛出错误

### save_mode_create_template

使用模板自动创建 Doris 表，
会根据上游数据类型和 schema 类型生成对应的建表语句，
默认模板可以按需调整。

```sql
CREATE TABLE IF NOT EXISTS `${database}`.`${table_name}`
(
    ${rowtype_fields}
) ENGINE = OLAP UNIQUE KEY (${rowtype_primary_key})
    DISTRIBUTED BY HASH (${rowtype_primary_key})
    PROPERTIES
(
    "replication_num" = "1"
);
```

如果模板中填写了自定义字段，例如添加 `id` 字段：

```sql
CREATE TABLE IF NOT EXISTS `${database}`.`${table_name}`
(
    id,
    ${rowtype_fields}
) ENGINE = OLAP UNIQUE KEY (${rowtype_primary_key})
    DISTRIBUTED BY HASH (${rowtype_primary_key})
    PROPERTIES
(
    "replication_num" = "1"
);
```

连接器会自动从上游获取对应类型完成填充，
并从 `rowtype_fields` 中删除 `id` 字段。该方法可用于自定义字段类型和属性。

可以使用以下占位符：

- `database`：用于获取上游 schema 中的数据库名
- `table_name`：用于获取上游 schema 中的表名
- `rowtype_fields`：用于获取上游 schema 中的所有字段，并自动映射为 Doris 字段定义
- `rowtype_primary_key`：用于获取上游 schema 中的主键（可能是列表）
- `rowtype_unique_key`：用于获取上游 schema 中的唯一键（可能是列表）

## 数据类型映射

|   Doris 数据类型   |             SeaTunnel 数据类型              |
|----------------|-----------------------------------------|
| BOOLEAN        | BOOLEAN                                 |
| TINYINT        | TINYINT                                 |
| SMALLINT       | SMALLINT<br/>TINYINT                    |
| INT            | INT<br/>SMALLINT<br/>TINYINT            |
| BIGINT         | BIGINT<br/>INT<br/>SMALLINT<br/>TINYINT |
| LARGEINT       | BIGINT<br/>INT<br/>SMALLINT<br/>TINYINT |
| FLOAT          | FLOAT                                   |
| DOUBLE         | DOUBLE<br/>FLOAT                        |
| DECIMAL        | DECIMAL<br/>DOUBLE<br/>FLOAT            |
| DATE           | DATE                                    |
| DATETIME       | TIMESTAMP                               |
| CHAR           | STRING                                  |
| VARCHAR        | STRING                                  |
| STRING         | STRING                                  |
| ARRAY          | ARRAY                                   |
| MAP            | MAP                                     |
| JSON           | STRING                                  |
| HLL            | 尚不支持                                    |
| BITMAP         | 尚不支持                                    |
| QUANTILE_STATE | 尚不支持                                    |
| STRUCT         | 尚不支持                                    |

#### 支持的导入数据格式

支持 CSV 和 JSON 两种格式。

## 任务示例

### 简单示例

> 下面的例子描述了向 Doris 写入多种数据类型，用户需要在下游创建对应的表。

```hocon
env {
  parallelism = 1
  job.mode = "BATCH"
  checkpoint.interval = 10000
}

source {
  FakeSource {
    row.num = 10
    map.size = 10
    array.size = 10
    bytes.length = 10
    string.length = 10
    schema = {
      fields {
        c_map = "map<string, array<int>>"
        c_array = "array<int>"
        c_string = string
        c_boolean = boolean
        c_tinyint = tinyint
        c_smallint = smallint
        c_int = int
        c_bigint = bigint
        c_float = float
        c_double = double
        c_decimal = "decimal(16, 1)"
        c_null = "null"
        c_bytes = bytes
        c_date = date
        c_timestamp = timestamp
      }
    }
  }
}

sink {
  Doris {
    fenodes = "doris_cdc_e2e:8030"
    username = root
    password = ""
    database = "test"
    table = "e2e_table_sink"
    sink.label-prefix = "test-cdc"
    sink.enable-2pc = "true"
    sink.enable-delete = "true"
    doris.config {
      format = "json"
      read_json_by_line = "true"
    }
  }
}
```

### CDC（变更数据捕获）事件

> 本示例定义了一个 SeaTunnel 同步任务，通过 FakeSource 自动生成数据并发送给 Doris Sink。FakeSource 使用 schema 和 score（int 类型）模拟 CDC 数据，Doris 需要提前创建名为 `test.e2e_table_sink` 的表。

```hocon
env {
  parallelism = 1
  job.mode = "BATCH"
  checkpoint.interval = 10000
}

source {
  FakeSource {
    schema = {
      fields {
        pk_id = bigint
        name = string
        score = int
        sex = boolean
        number = tinyint
        height = float
        sight = double
        create_time = date
        update_time = timestamp
      }
    }
    rows = [
      {
        kind = INSERT
        fields = [1, "A", 100, true, 1, 170.0, 4.3, "2020-02-02", "2020-02-02T02:02:02"]
      },
      {
        kind = INSERT
        fields = [2, "B", 100, true, 1, 170.0, 4.3, "2020-02-02", "2020-02-02T02:02:02"]
      },
      {
        kind = INSERT
        fields = [3, "C", 100, true, 1, 170.0, 4.3, "2020-02-02", "2020-02-02T02:02:02"]
      },
      {
        kind = UPDATE_BEFORE
        fields = [1, "A", 100, true, 1, 170.0, 4.3, "2020-02-02", "2020-02-02T02:02:02"]
      },
      {
        kind = UPDATE_AFTER
        fields = [1, "A_1", 100, true, 1, 170.0, 4.3, "2020-02-02", "2020-02-02T02:02:02"]
      },
      {
        kind = DELETE
        fields = [2, "B", 100, true, 1, 170.0, 4.3, "2020-02-02", "2020-02-02T02:02:02"]
      }
    ]
  }
}

sink {
  Doris {
    fenodes = "doris_cdc_e2e:8030"
    username = root
    password = ""
    database = "test"
    table = "e2e_table_sink"
    sink.label-prefix = "test-cdc"
    sink.enable-2pc = "true"
    sink.enable-delete = "true"
    doris.config {
      format = "json"
      read_json_by_line = "true"
    }
  }
}
```

### 使用 JSON 格式导入数据

```hocon
sink {
  Doris {
    fenodes = "e2e_dorisdb:8030"
    username = root
    password = ""
    database = "test"
    table = "e2e_table_sink"
    sink.enable-2pc = "true"
    sink.label-prefix = "test_json"
    doris.config = {
      format = "json"
      read_json_by_line = "true"
    }
  }
}
```

### 使用 CSV 格式导入数据

```hocon
sink {
  Doris {
    fenodes = "e2e_dorisdb:8030"
    username = root
    password = ""
    database = "test"
    table = "e2e_table_sink"
    sink.enable-2pc = "true"
    sink.label-prefix = "test_csv"
    doris.config = {
      format = "csv"
      column_separator = ","
    }
  }
}
```

## 变更日志

### 2.3.0-beta 2022-10-20

- 添加 Doris sink 连接器

### Next version

- [Improve] Change Doris Config Prefix [3856](https://github.com/apache/seatunnel/pull/3856)
- [Improve] Refactor some Doris Sink code as well as support 2pc and cdc [4235](https://github.com/apache/seatunnel/pull/4235)

:::tip

PR 4235 is an incompatible modification to PR 3856. Please refer to PR 4235 to use the new Doris connector

:::

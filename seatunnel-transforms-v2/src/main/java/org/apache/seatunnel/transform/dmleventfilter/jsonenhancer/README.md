# CDC JSON Enhancers

本包包含各种 CDC JSON 格式的增强器（Debezium、Canal、OGG 等）。
比如要添加新 json 格式：MAXWELL_JSON

## ⚠️ 添加新 CDC JSON 格式的步骤

### 第 1 步：实现 Enhancer
```java
public class MaxwellJsonEnhancer extends AbstractCdcJsonEnhancer {
    @Override
    public String getFormatName() {
        return "MAXWELL_JSON";  // 必须匹配 source 配置的 format 名称
    }

    @Override
    public boolean canHandle(JsonNode valueNode) {
        // 检查 JSON 结构是否匹配
    }

    // ... 实现其他方法
}
```

### 第 2 步：注册到 Manager
在 `CdcJsonEnhancerManager.initializeEnhancers()` 中添加：
```java
enhancers.add(new MaxwellJsonEnhancer());
```

### 第 3 步：添加格式映射
在 `DMLEventFilterTransform.TableProcessor.createEnhancerByFormat()` 中添加：
```java
case "MAXWELL_JSON":
    return new MaxwellJsonEnhancer();
```

### 第 4 步（可选）：更新 Kafka 枚举
如果 Kafka source 会使用，在 `connector-kafka/../MessageFormat.java` 中添加：
```java
MAXWELL_JSON,
```

## 📋 现有格式

| Enhancer | Format Name | Priority | 说明 |
|----------|-------------|----------|------|
| `DebeziumJsonEnhancer` | DEBEZIUM_JSON | 1 | 标准 Debezium 格式 |
| `CompatibleDebeziumJsonEnhancer` | COMPATIBLE_DEBEZIUM_JSON | 2 | 兼容 Debezium 格式 |
| `CanalJsonEnhancer` | CANAL_JSON | 3 | Alibaba Canal 格式 |
| `OggJsonEnhancer` | OGG_JSON | 4 | Oracle GoldenGate 格式 |
| `KingbaseJsonEnhancer` | KINGBASE_JSON | 10 | Kingbase 格式 |
| `CustomCdcJsonEnhancer` | CUSTOM_CDC_JSON | 100 | 自定义格式 |

## 🔍 优先级说明

- **1-10**: 标准、广泛使用的格式
- **11-50**: 厂商特定格式
- **51-99**: 实验性或较少用的格式
- **100+**: 自定义/兜底格式

优先级越低，在自动探测时越先被检查。

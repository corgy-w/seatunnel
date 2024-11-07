/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.format.cdc.custom.json;

import org.apache.seatunnel.api.serialization.SerializationSchema;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.Struct;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.OP_DELETE;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.OP_INSERT;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.OP_READ;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.OP_UPDATE;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.SOURCE_CONNECTOR;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.SOURCE_CONNECTOR_DAMENG;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.SOURCE_CONNECTOR_MYSQL;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.SOURCE_CONNECTOR_ORACLE;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.SOURCE_CONNECTOR_POSTGRES;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.SOURCE_DAMENG_SCN;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.SOURCE_DATABASE;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.SOURCE_MYSQL_FILE;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.SOURCE_MYSQL_POS;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.SOURCE_ORACLE_SCN;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.SOURCE_POSTGRES_LSN;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.SOURCE_SCHEMA;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.SOURCE_TABLE;
import static org.apache.seatunnel.format.cdc.custom.json.DebeziumJsonConverter.SOURCE_TS_MS;

public class CustomJsonSerializationSchema implements SerializationSchema {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .append(ISO_LOCAL_DATE)
                    .appendLiteral(' ')
                    .append(ISO_LOCAL_TIME)
                    .toFormatter();
    private static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private boolean isKey;
    private DebeziumJsonConverter debeziumJsonConverter;

    public CustomJsonSerializationSchema(SeaTunnelRowType rowType, boolean isKey) {
        if (!Arrays.equals(rowType.getFieldNames(), new String[] {"topic", "key", "value"})) {
            throw new UnsupportedOperationException(
                    "JXDJsonSerializationSchema only supports 'topic', 'key', 'value' fields. "
                            + rowType);
        }
        this.isKey = isKey;
        this.debeziumJsonConverter = new DebeziumJsonConverter(true, true);
    }

    @SneakyThrows
    @Override
    public byte[] serialize(SeaTunnelRow row) {
        try {
            String key = (String) row.getField(1);
            Map<String, Object> keys = Collections.emptyMap();
            if (!StringUtils.isEmpty(key)) {
                keys = parseDebeziumRecordKey(key);
            }
            if (isKey) {
                return keys.isEmpty() ? null : OBJECT_MAPPER.writeValueAsBytes(keys);
            }

            String value = (String) row.getField(2);
            SchemaAndValue valueSchemaAndaValue = debeziumJsonConverter.deserializeValue(value);
            Struct valueStruct = (Struct) valueSchemaAndaValue.value();
            String op = valueStruct.getString(DebeziumJsonConverter.OPERATION);
            Struct source = valueStruct.getStruct(DebeziumJsonConverter.SOURCE);
            Struct before = valueStruct.getStruct(DebeziumJsonConverter.BEFORE);
            Struct after = valueStruct.getStruct(DebeziumJsonConverter.AFTER);

            CustomJsonRecord customJsonRecord =
                    CustomJsonRecord.builder()
                            .table(parseTablePath(source).toUpperCase())
                            .primaryKeys(new ArrayList<>(keys.keySet()))
                            .before(before == null ? null : parseData(before))
                            .after(after == null ? null : parseData(after))
                            .op(parseOp(op))
                            .opTs(parseOpTs(source))
                            .currentTs(parseCurrentTs())
                            .pos(parsePos(keys, op, source, after))
                            .build();
            return OBJECT_MAPPER.writeValueAsBytes(customJsonRecord);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to serialize row: " + row, e);
        }
    }

    private Map<String, Object> parseDebeziumRecordKey(String key) {
        Map<String, Object> keyMap = new LinkedHashMap<>();

        SchemaAndValue keySchemaAndaValue = debeziumJsonConverter.deserializeKey(key);
        Struct keyStruct = (Struct) keySchemaAndaValue.value();
        for (Field field : keySchemaAndaValue.schema().fields()) {
            Object value = parseField(keyStruct, field);
            keyMap.put(field.name(), value);
        }
        return keyMap;
    }

    private String parseTablePath(Struct source) {
        String connector = (String) source.get(SOURCE_CONNECTOR);
        switch (connector) {
            case SOURCE_CONNECTOR_ORACLE:
            case SOURCE_CONNECTOR_DAMENG:
                return source.get(SOURCE_SCHEMA) + "." + source.get(SOURCE_TABLE);
            case SOURCE_CONNECTOR_MYSQL:
                return source.get(SOURCE_DATABASE) + "." + source.get(SOURCE_TABLE);
            case SOURCE_CONNECTOR_POSTGRES:
                return source.get(SOURCE_SCHEMA) + "." + source.get(SOURCE_TABLE);
            default:
                throw new UnsupportedOperationException("Unsupported connector: " + connector);
        }
    }

    private String parseOp(String op) {
        switch (op) {
            case OP_READ:
                return "C";
            case OP_INSERT:
                return "I";
            case OP_UPDATE:
                return "U";
            case OP_DELETE:
                return "D";
            default:
                throw new UnsupportedOperationException("Unsupported op type: " + op);
        }
    }

    private String parseCurrentTs() {
        Date date = new Date();
        return DateFormatUtils.format(date, "yyyy-MM-dd HH:mm:ss.SSSSSS");
    }

    private String parseOpTs(Struct source) {
        long tsMs = (long) source.get(SOURCE_TS_MS);
        Date date = new Date(tsMs);
        return DateFormatUtils.format(date, "yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
    }

    private String parsePos(Map<String, Object> keys, String op, Struct source, Struct after) {
        if (OP_READ.equalsIgnoreCase(op)) {
            String table = parseTablePath(source);
            if (!keys.isEmpty()) {
                return "snapshot_read:" + (table + ":" + keys).hashCode();
            }
            return "snapshot_read:" + (table + ":" + after).hashCode();
        }

        String connector = (String) source.get(SOURCE_CONNECTOR);
        switch (connector) {
            case SOURCE_CONNECTOR_MYSQL:
                Object binlogFile = source.get(SOURCE_MYSQL_FILE);
                return binlogFile + ":" + source.get(SOURCE_MYSQL_POS);
            case SOURCE_CONNECTOR_ORACLE:
                return source.get(SOURCE_ORACLE_SCN).toString();
            case SOURCE_CONNECTOR_DAMENG:
                return source.get(SOURCE_DAMENG_SCN).toString();
            case SOURCE_CONNECTOR_POSTGRES:
                return source.get(SOURCE_POSTGRES_LSN).toString();
            default:
                throw new UnsupportedOperationException("Unsupported connector: " + connector);
        }
    }

    private Map<String, Object> parseData(Struct struct) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (Field field : struct.schema().fields()) {
            Object value = parseField(struct, field);
            data.put(field.name(), value);
        }
        return data;
    }

    private Object parseField(Struct struct, Field field) {
        String schemaName = field.schema().name();
        switch (field.schema().type()) {
            case INT32:
                if (schemaName != null && "io.debezium.time.Date".equalsIgnoreCase(schemaName)) {
                    Integer epochDay = struct.getInt32(field.name());
                    if (epochDay == null) {
                        return null;
                    }
                    return LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ISO_DATE);
                }
                return struct.get(field);
            case INT64:
                if (schemaName != null) {
                    Long epochMilli = struct.getInt64(field.name());
                    if (epochMilli == null) {
                        return null;
                    }
                    if ("io.debezium.time.Timestamp".equalsIgnoreCase(schemaName)) {
                        return Instant.ofEpochMilli(epochMilli)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDateTime()
                                .format(DATE_TIME_FORMATTER);
                    } else if ("io.debezium.time.MicroTimestamp".equalsIgnoreCase(schemaName)) {
                        return Instant.ofEpochMilli(epochMilli / 6)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDateTime()
                                .format(DATE_TIME_FORMATTER);
                    }
                }
                return struct.get(field);
            case STRUCT:
                if (schemaName != null
                        && "io.debezium.data.VariableScaleDecimal".equals(schemaName)) {
                    Struct variableScaleDecimal = struct.getStruct(field.name());
                    if (variableScaleDecimal == null) {
                        return null;
                    }
                    return new BigDecimal(
                            new BigInteger(variableScaleDecimal.getBytes("value")),
                            variableScaleDecimal.getInt32("scale"));
                }
                return struct.get(field);
            default:
                return struct.get(field);
        }
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.kafka.source;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.source.Collector;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.format.json.customcdc.CustomCdcJsonSourceDeserializationSchema;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class KafkaSourceConfigCustomCdcJsonTest {

    @Test
    void shouldBuildMultiTableMetadataForCustomCdcJson() throws Exception {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("bootstrap.servers", "localhost:9092");
        configMap.put(
                "table_list",
                Collections.singletonList(
                        new HashMap<String, Object>() {
                            {
                                put("topic", "mysqlcdc_to_kafka");
                                put("format", "CUSTOM_CDC_JSON");
                                put("schema_path", "$.schema");
                                put("table_path", "$.table");
                                put("operation_path", "$.operation");
                                put(
                                        "custom_cdc_operation_type_mapping",
                                        new HashMap<String, String>() {
                                            {
                                                put("I", "INSERT");
                                                put("U", "UPDATE_AFTER");
                                                put("D", "DELETE");
                                            }
                                        });
                                put("before_path", "$.message.beforeData");
                                put("after_path", "$.message.afterData");
                                put(
                                        "schema_list",
                                        Arrays.asList(
                                                new HashMap<String, Object>() {
                                                    {
                                                        put("table", "student");
                                                        put(
                                                                "json_field",
                                                                new HashMap<String, String>() {
                                                                    {
                                                                        put(
                                                                                "studentId",
                                                                                "$.StudentId");
                                                                        put(
                                                                                "studentName",
                                                                                "$.StudentName");
                                                                    }
                                                                });
                                                        put(
                                                                "columns",
                                                                Arrays.asList(
                                                                        new HashMap<
                                                                                String, Object>() {
                                                                            {
                                                                                put(
                                                                                        "name",
                                                                                        "studentId");
                                                                                put(
                                                                                        "type",
                                                                                        "string");
                                                                                put(
                                                                                        "columnLength",
                                                                                        255L);
                                                                                put(
                                                                                        "nullable",
                                                                                        false);
                                                                                put("comment", "");
                                                                            }
                                                                        },
                                                                        new HashMap<
                                                                                String, Object>() {
                                                                            {
                                                                                put(
                                                                                        "name",
                                                                                        "studentName");
                                                                                put(
                                                                                        "type",
                                                                                        "string");
                                                                                put(
                                                                                        "columnLength",
                                                                                        255L);
                                                                                put(
                                                                                        "nullable",
                                                                                        true);
                                                                                put("comment", "");
                                                                            }
                                                                        }));
                                                    }
                                                },
                                                new HashMap<String, Object>() {
                                                    {
                                                        put("table", "teacher");
                                                        put(
                                                                "json_field",
                                                                new HashMap<String, String>() {
                                                                    {
                                                                        put(
                                                                                "teacherId",
                                                                                "$.TeacherId");
                                                                        put(
                                                                                "teacherName",
                                                                                "$.TeacherName");
                                                                    }
                                                                });
                                                        put(
                                                                "columns",
                                                                Arrays.asList(
                                                                        new HashMap<
                                                                                String, Object>() {
                                                                            {
                                                                                put(
                                                                                        "name",
                                                                                        "teacherId");
                                                                                put(
                                                                                        "type",
                                                                                        "string");
                                                                                put(
                                                                                        "columnLength",
                                                                                        255L);
                                                                                put(
                                                                                        "nullable",
                                                                                        false);
                                                                                put("comment", "");
                                                                            }
                                                                        },
                                                                        new HashMap<
                                                                                String, Object>() {
                                                                            {
                                                                                put(
                                                                                        "name",
                                                                                        "teacherName");
                                                                                put(
                                                                                        "type",
                                                                                        "string");
                                                                                put(
                                                                                        "columnLength",
                                                                                        255L);
                                                                                put(
                                                                                        "nullable",
                                                                                        true);
                                                                                put("comment", "");
                                                                            }
                                                                        }));
                                                    }
                                                }));
                            }
                        }));

        KafkaSourceConfig kafkaSourceConfig =
                new KafkaSourceConfig(ReadonlyConfig.fromMap(configMap));

        Assertions.assertEquals(1, kafkaSourceConfig.getMapMetadata().size());
        ConsumerMetadata metadata = kafkaSourceConfig.getMapMetadata().values().iterator().next();
        Assertions.assertEquals("mysqlcdc_to_kafka", metadata.getTopic());
        Assertions.assertTrue(
                metadata.getDeserializationSchema()
                        instanceof CustomCdcJsonSourceDeserializationSchema);
        Assertions.assertEquals(2, metadata.getCatalogTable().size());
        Assertions.assertEquals(
                Arrays.asList("default.student", "default.teacher"),
                metadata.getCatalogTable().stream()
                        .map(CatalogTable::getTablePath)
                        .map(TablePath::toString)
                        .sorted()
                        .collect(Collectors.toList()));

        ListCollector collector = new ListCollector();
        metadata.getDeserializationSchema()
                .deserialize(
                        ("{"
                                        + "\"schema\":\"public\","
                                        + "\"table\":\"student\","
                                        + "\"operation\":\"I\","
                                        + "\"message\":{\"beforeData\":null,"
                                        + "\"afterData\":{\"StudentId\":\"1\",\"StudentName\":\"Alice\"}}"
                                        + "}")
                                .getBytes(StandardCharsets.UTF_8),
                        collector);
        Assertions.assertEquals(1, collector.rows.size());
        Assertions.assertEquals(RowKind.INSERT, collector.rows.get(0).getRowKind());
        Assertions.assertEquals("default.student", collector.rows.get(0).getTableId());
    }

    @Test
    void shouldBuildMapMetadataFromWebCustomCdcJsonConfig() {
        KafkaSourceConfig kafkaSourceConfig =
                new KafkaSourceConfig(ReadonlyConfig.fromMap(createWebCustomCdcJsonConfig()));

        Assertions.assertEquals(1, kafkaSourceConfig.getMapMetadata().size());
        TablePath metadataKey = kafkaSourceConfig.getMapMetadata().keySet().iterator().next();
        Assertions.assertEquals(TablePath.of(null, "attunity_multi_table"), metadataKey);

        ConsumerMetadata metadata = kafkaSourceConfig.getMapMetadata().get(metadataKey);
        Assertions.assertEquals("attunity_multi_table", metadata.getTopic());
        Assertions.assertEquals("SeaTunnel-Consumer-Group", metadata.getConsumerGroup());
        Assertions.assertEquals(3, metadata.getCatalogTable().size());
        Assertions.assertEquals(
                Arrays.asList(
                        "a_bank.attunity_accounts",
                        "b_bank.attunity_accounts",
                        "c_bank.attunity_accounts"),
                metadata.getCatalogTable().stream()
                        .map(CatalogTable::getTablePath)
                        .map(TablePath::toString)
                        .sorted()
                        .collect(Collectors.toList()));
        Assertions.assertEquals(
                "a_bank", metadata.getCatalogTable().get(0).getTableId().getDatabaseName());
        Assertions.assertNull(metadata.getCatalogTable().get(0).getTableId().getSchemaName());
        Assertions.assertEquals(
                "attunity_accounts", metadata.getCatalogTable().get(0).getTableId().getTableName());
        Assertions.assertEquals(
                Arrays.asList(
                        "account_id",
                        "account_name",
                        "account_status",
                        "balance",
                        "upd_timestamp",
                        "source_schema",
                        "source_table"),
                metadata.getCatalogTable().get(0).getTableSchema().getColumns().stream()
                        .map(column -> column.getName())
                        .collect(Collectors.toList()));
        Assertions.assertEquals(
                " account_id",
                metadata.getCatalogTable().get(1).getTableSchema().getColumns().get(0).getName());
    }

    private static Map<String, Object> createWebCustomCdcJsonConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("start_mode", "group_offsets");
        config.put("consumer.group", "SeaTunnel-Consumer-Group");
        config.put("parallelism", 3);
        config.put("partition-discovery.interval-millis", -1);
        config.put("commit_on_checkpoint", "true");
        config.put("datasourceName", "Kafka");
        config.put("updateFieldType", null);
        config.put("wt_web_control_key_autoCreateShow", false);
        config.put("wt_web_control_key_autoCreate", false);
        config.put("wt_web_control_key_autoTable", false);
        config.put("sceneMode", "MULTIPLE_TABLE");
        config.put("isVirtualTableDatasource", true);
        config.put("isPhysicsTableDatasource", false);
        config.put(
                "table_list",
                Collections.singletonList(
                        new LinkedHashMap<String, Object>() {
                            {
                                put("topic", "attunity_multi_table");
                                put("format", "CUSTOM_CDC_JSON");
                                put("consumer.group", "SeaTunnel-Consumer-Group");
                                put("start_mode", "group_offsets");
                                put("commit_on_checkpoint", "true");
                                put("schema_path", "$.schema");
                                put("table_path", "$.message.data.tablename");
                                put("operation_path", "$.message.headers.operation");
                                put("before_path", "$.message.beforeData");
                                put("after_path", "$.message.data");
                                put(
                                        "custom_cdc_operation_type_mapping",
                                        new LinkedHashMap<String, String>() {
                                            {
                                                put("INSERT", "INSERT");
                                                put("UPDATE", "UPDATE");
                                                put("DELETE", "DELETE");
                                            }
                                        });
                                put(
                                        "schema_list",
                                        Arrays.asList(
                                                createAttunitySchema(
                                                        "a_bank.attunity_accounts", "account_id"),
                                                createAttunitySchema(
                                                        "b_bank.attunity_accounts", " account_id"),
                                                createAttunitySchema(
                                                        "c_bank.attunity_accounts",
                                                        " account_id")));
                            }
                        }));
        config.put("displayName", "Kafka");
        config.put("bootstrap.servers", "localhost:9092");
        config.put("kafka.config", Collections.emptyMap());
        config.put("plugin_name", "Kafka");
        return config;
    }

    private static Map<String, Object> createAttunitySchema(String table, String accountIdColumn) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("table", table);
        schema.put(
                "json_field",
                new LinkedHashMap<String, String>() {
                    {
                        put(accountIdColumn, "$.ACCOUNT_ID");
                        put("account_name", "$.ACCOUNT_NAME");
                        put("account_status", "$.ACCOUNT_STATUS");
                        put("balance", "$.BALANCE");
                        put("upd_timestamp", "$.UPD_TIMESTAMP");
                        put("source_schema", "$.schema");
                        put("source_table", "$.tablename");
                    }
                });
        schema.put(
                "columns",
                Arrays.asList(
                        createColumn(accountIdColumn),
                        createColumn("account_name"),
                        createColumn("account_status"),
                        createColumn("balance"),
                        createColumn("upd_timestamp"),
                        createColumn("source_schema"),
                        createColumn("source_table")));
        return schema;
    }

    private static Map<String, Object> createColumn(String name) {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("name", name);
        column.put("type", "string");
        column.put("nullable", false);
        return column;
    }

    private static class ListCollector implements Collector<SeaTunnelRow> {
        private final List<SeaTunnelRow> rows = new ArrayList<>();
        private final Object checkpointLock = new Object();

        @Override
        public void collect(SeaTunnelRow record) {
            rows.add(record);
        }

        @Override
        public Object getCheckpointLock() {
            return checkpointLock;
        }
    }
}

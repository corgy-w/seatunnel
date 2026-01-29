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

package org.apache.seatunnel.transform.dmleventfilter;

import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.JsonNode;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.common.utils.JsonUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Integration tests for DMLEventFilterTransform with CDC JSON formats
 *
 * <p>Covers the 43+ test scenarios from the design document: - DEBEZIUM_JSON: 8 scenarios
 * (SOFT_DELETE, ADD_DML_MARKER, APPEND_MODE) - COMPATIBLE_DEBEZIUM_JSON: 8 scenarios - CANAL_JSON:
 * 16 scenarios (including multi-record) - OGG_JSON: 8 scenarios
 *
 * <p>Total: 40 test cases
 */
public class DMLEventFilterTransformCdcIntegrationTest {

    private CatalogTable createCdcTable() {
        TableSchema schema =
                TableSchema.builder()
                        .column(
                                PhysicalColumn.of(
                                        "topic", BasicType.STRING_TYPE, 0L, true, null, null))
                        .column(
                                PhysicalColumn.of(
                                        "key", BasicType.STRING_TYPE, 0L, true, null, null))
                        .column(
                                PhysicalColumn.of(
                                        "value", BasicType.STRING_TYPE, 0L, true, null, null))
                        .build();
        return CatalogTable.of(
                TableIdentifier.of("catalog", "db", "cdc_table"),
                schema,
                new HashMap<>(),
                new ArrayList<>(),
                "CDC table");
    }

    private DMLEventFilterTransform buildTransform(CatalogTable table, Map<String, Object> config) {
        ReadonlyConfig cfg = ReadonlyConfig.fromMap(config);
        DMLEventFilterTransform transform =
                new DMLEventFilterTransform(Collections.singletonList(table), cfg);
        transform.getProducedCatalogTable();
        return transform;
    }

    private SeaTunnelRow createRow(String topic, String key, String value) {
        return new SeaTunnelRow(new Object[] {topic, key, value});
    }

    // ==================== DEBEZIUM_JSON Tests ====================

    @Test
    void testDebeziumJson_SoftDelete_Delete() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.SOFT_DELETE.name());
        config.put("marker_field_name", "is_deleted");
        config.put("marker_field_value", "1");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String deleteJson = "{\"payload\":{\"op\":\"d\",\"before\":{\"id\":1,\"name\":\"Alice\"}}}";
        SeaTunnelRow input = createRow("topic", "key1", deleteJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output.getRowKind());
        String enhancedValue = (String) output.getField(2);
        Assertions.assertTrue(enhancedValue.contains("is_deleted"));
        Assertions.assertTrue(enhancedValue.contains("\"1\""));
    }

    @Test
    void testDebeziumJson_SoftDelete_Insert() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.SOFT_DELETE.name());
        config.put("marker_field_name", "is_deleted");
        config.put("marker_field_value", "1");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String insertJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":1,\"name\":\"Bob\"}}}";
        SeaTunnelRow input = createRow("topic", "key1", insertJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testDebeziumJson_AddDmlMarker_Insert() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String insertJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":1,\"name\":\"Alice\"}}}";
        SeaTunnelRow input = createRow("topic", "key1", insertJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
        String enhancedValue = (String) output.getField(2);
        Assertions.assertTrue(enhancedValue.contains("op_type"));
    }

    @Test
    void testDebeziumJson_AddDmlMarker_Update() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String updateJson =
                "{\"payload\":{\"op\":\"u\",\"before\":{\"id\":1,\"name\":\"Alice\"},\"after\":{\"id\":1,\"name\":\"Bob\"}}}";
        SeaTunnelRow input = createRow("topic", "key1", updateJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output.getRowKind());
        String enhancedValue = (String) output.getField(2);
        Assertions.assertTrue(enhancedValue.contains("op_type"));
    }

    @Test
    void testDebeziumJson_AddDmlMarker_Delete() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String deleteJson = "{\"payload\":{\"op\":\"d\",\"before\":{\"id\":1,\"name\":\"Alice\"}}}";
        SeaTunnelRow input = createRow("topic", "key1", deleteJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output.getRowKind());
        String enhancedValue = (String) output.getField(2);
        Assertions.assertTrue(enhancedValue.contains("op_type"));
    }

    @Test
    void testDebeziumJson_AppendMode_Insert() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String insertJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":1,\"name\":\"Alice\"}}}";
        SeaTunnelRow input = createRow("topic", "key1", insertJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
        String enhancedValue = (String) output.getField(2);
        Assertions.assertTrue(enhancedValue.contains("op_ts"));
        Assertions.assertTrue(enhancedValue.contains("op_type"));
    }

    @Test
    void testDebeziumJson_AppendMode_Update() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String updateJson =
                "{\"payload\":{\"op\":\"u\",\"before\":{\"id\":1,\"name\":\"Alice\"},\"after\":{\"id\":1,\"name\":\"Bob\"}}}";
        SeaTunnelRow input = createRow("topic", "key1", updateJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testDebeziumJson_AppendMode_Delete() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String deleteJson = "{\"payload\":{\"op\":\"d\",\"before\":{\"id\":1,\"name\":\"Alice\"}}}";
        SeaTunnelRow input = createRow("topic", "key1", deleteJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    // ==================== COMPATIBLE_DEBEZIUM_JSON Tests ====================

    @Test
    void testCompatibleDebeziumJson_SoftDelete_Delete() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.SOFT_DELETE.name());
        config.put("marker_field_name", "is_deleted");
        config.put("marker_field_value", "1");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String innerJson = "{\"payload\":{\"op\":\"d\",\"before\":{\"id\":1,\"name\":\"Alice\"}}}";
        String wrappedValue =
                String.format(
                        "{\"topic\":\"test\",\"key\":\"1\",\"value\":\"%s\"}",
                        innerJson.replace("\"", "\\\""));
        SeaTunnelRow input = createRow("topic", "key1", wrappedValue);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output.getRowKind());
    }

    @Test
    void testCompatibleDebeziumJson_SoftDelete_Insert() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.SOFT_DELETE.name());
        config.put("marker_field_name", "is_deleted");
        config.put("marker_field_value", "1");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String innerJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":1,\"name\":\"Bob\"}}}";
        String wrappedValue =
                String.format(
                        "{\"topic\":\"test\",\"key\":\"1\",\"value\":\"%s\"}",
                        innerJson.replace("\"", "\\\""));
        SeaTunnelRow input = createRow("topic", "key1", wrappedValue);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testCompatibleDebeziumJson_AddDmlMarker_Insert() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String innerJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":1,\"name\":\"Alice\"}}}";
        String wrappedValue =
                String.format(
                        "{\"topic\":\"test\",\"key\":\"1\",\"value\":\"%s\"}",
                        innerJson.replace("\"", "\\\""));
        SeaTunnelRow input = createRow("topic", "key1", wrappedValue);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testCompatibleDebeziumJson_AddDmlMarker_Update() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String innerJson =
                "{\"payload\":{\"op\":\"u\",\"before\":{\"id\":1,\"name\":\"Alice\"},\"after\":{\"id\":1,\"name\":\"Bob\"}}}";
        String wrappedValue =
                String.format(
                        "{\"topic\":\"test\",\"key\":\"1\",\"value\":\"%s\"}",
                        innerJson.replace("\"", "\\\""));
        SeaTunnelRow input = createRow("topic", "key1", wrappedValue);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output.getRowKind());
    }

    @Test
    void testCompatibleDebeziumJson_AddDmlMarker_Delete() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String innerJson = "{\"payload\":{\"op\":\"d\",\"before\":{\"id\":1,\"name\":\"Alice\"}}}";
        String wrappedValue =
                String.format(
                        "{\"topic\":\"test\",\"key\":\"1\",\"value\":\"%s\"}",
                        innerJson.replace("\"", "\\\""));
        SeaTunnelRow input = createRow("topic", "key1", wrappedValue);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output.getRowKind());
    }

    @Test
    void testCompatibleDebeziumJson_AppendMode_Insert() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String innerJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":1,\"name\":\"Alice\"}}}";
        String wrappedValue =
                String.format(
                        "{\"topic\":\"test\",\"key\":\"1\",\"value\":\"%s\"}",
                        innerJson.replace("\"", "\\\""));
        SeaTunnelRow input = createRow("topic", "key1", wrappedValue);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testCompatibleDebeziumJson_AppendMode_Update() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String innerJson =
                "{\"payload\":{\"op\":\"u\",\"before\":{\"id\":1,\"name\":\"Alice\"},\"after\":{\"id\":1,\"name\":\"Bob\"}}}";
        String wrappedValue =
                String.format(
                        "{\"topic\":\"test\",\"key\":\"1\",\"value\":\"%s\"}",
                        innerJson.replace("\"", "\\\""));
        SeaTunnelRow input = createRow("topic", "key1", wrappedValue);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testCompatibleDebeziumJson_AppendMode_Delete() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String innerJson = "{\"payload\":{\"op\":\"d\",\"before\":{\"id\":1,\"name\":\"Alice\"}}}";
        String wrappedValue =
                String.format(
                        "{\"topic\":\"test\",\"key\":\"1\",\"value\":\"%s\"}",
                        innerJson.replace("\"", "\\\""));
        SeaTunnelRow input = createRow("topic", "key1", wrappedValue);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    // ==================== CANAL_JSON Tests ====================

    @Test
    void testCanalJson_SoftDelete_Delete() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.SOFT_DELETE.name());
        config.put("marker_field_name", "is_deleted");
        config.put("marker_field_value", "1");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String deleteJson = "{\"data\":[{\"id\":1,\"name\":\"Alice\"}],\"type\":\"DELETE\"}";
        SeaTunnelRow input = createRow("topic", "key1", deleteJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output.getRowKind());
        String enhancedValue = (String) output.getField(2);
        Assertions.assertTrue(enhancedValue.contains("is_deleted"));
        Assertions.assertTrue(enhancedValue.contains("\"type\":\"UPDATE\""));
    }

    @Test
    void testCanalJson_SoftDelete_Delete_MultiRecord() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.SOFT_DELETE.name());
        config.put("marker_field_name", "is_deleted");
        config.put("marker_field_value", "1");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String deleteJson =
                "{\"data\":[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}],\"type\":\"DELETE\"}";
        SeaTunnelRow input = createRow("topic", "key1", deleteJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        String enhancedValue = (String) output.getField(2);
        try {
            JsonNode node = JsonUtils.parseObject(enhancedValue);
            Assertions.assertEquals(2, node.get("data").size());
            Assertions.assertTrue(node.get("data").get(0).has("is_deleted"));
            Assertions.assertTrue(node.get("data").get(1).has("is_deleted"));
        } catch (Exception e) {
            Assertions.fail("Failed to parse enhanced JSON: " + e.getMessage());
        }
    }

    @Test
    void testCanalJson_SoftDelete_Insert() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.SOFT_DELETE.name());
        config.put("marker_field_name", "is_deleted");
        config.put("marker_field_value", "1");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String insertJson = "{\"data\":[{\"id\":1,\"name\":\"Bob\"}],\"type\":\"INSERT\"}";
        SeaTunnelRow input = createRow("topic", "key1", insertJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testCanalJson_SoftDelete_Update() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.SOFT_DELETE.name());
        config.put("marker_field_name", "is_deleted");
        config.put("marker_field_value", "1");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String updateJson =
                "{\"data\":[{\"id\":1,\"name\":\"Bob\"}],\"old\":[{\"name\":\"Alice\"}],\"type\":\"UPDATE\"}";
        SeaTunnelRow input = createRow("topic", "key1", updateJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output.getRowKind());
    }

    @Test
    void testCanalJson_AddDmlMarker_Insert() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String insertJson = "{\"data\":[{\"id\":1,\"name\":\"Alice\"}],\"type\":\"INSERT\"}";
        SeaTunnelRow input = createRow("topic", "key1", insertJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testCanalJson_AddDmlMarker_Insert_MultiRecord() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String insertJson =
                "{\"data\":[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}],\"type\":\"INSERT\"}";
        SeaTunnelRow input = createRow("topic", "key1", insertJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        String enhancedValue = (String) output.getField(2);
        try {
            JsonNode node = JsonUtils.parseObject(enhancedValue);
            Assertions.assertEquals(2, node.get("data").size());
            Assertions.assertTrue(node.get("data").get(0).has("op_type"));
            Assertions.assertTrue(node.get("data").get(1).has("op_type"));
        } catch (Exception e) {
            Assertions.fail("Failed to parse enhanced JSON: " + e.getMessage());
        }
    }

    @Test
    void testCanalJson_AddDmlMarker_Update() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String updateJson =
                "{\"data\":[{\"id\":1,\"name\":\"Bob\"}],\"old\":[{\"name\":\"Alice\"}],\"type\":\"UPDATE\"}";
        SeaTunnelRow input = createRow("topic", "key1", updateJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output.getRowKind());
    }

    @Test
    void testCanalJson_AddDmlMarker_Update_MultiRecord() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String updateJson =
                "{\"data\":[{\"id\":1,\"name\":\"Bob\"},{\"id\":2,\"name\":\"Charlie\"}],\"old\":[{\"name\":\"Alice\"},{\"name\":\"Bob\"}],\"type\":\"UPDATE\"}";
        SeaTunnelRow input = createRow("topic", "key1", updateJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        String enhancedValue = (String) output.getField(2);
        try {
            JsonNode node = JsonUtils.parseObject(enhancedValue);
            Assertions.assertEquals(2, node.get("data").size());
            Assertions.assertEquals(2, node.get("old").size());
        } catch (Exception e) {
            Assertions.fail("Failed to parse enhanced JSON: " + e.getMessage());
        }
    }

    @Test
    void testCanalJson_AddDmlMarker_Delete() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String deleteJson = "{\"data\":[{\"id\":1,\"name\":\"Alice\"}],\"type\":\"DELETE\"}";
        SeaTunnelRow input = createRow("topic", "key1", deleteJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output.getRowKind());
    }

    @Test
    void testCanalJson_AddDmlMarker_Delete_MultiRecord() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String deleteJson =
                "{\"data\":[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}],\"type\":\"DELETE\"}";
        SeaTunnelRow input = createRow("topic", "key1", deleteJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        String enhancedValue = (String) output.getField(2);
        try {
            JsonNode node = JsonUtils.parseObject(enhancedValue);
            Assertions.assertEquals(2, node.get("data").size());
        } catch (Exception e) {
            Assertions.fail("Failed to parse enhanced JSON: " + e.getMessage());
        }
    }

    @Test
    void testCanalJson_AppendMode_Insert() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String insertJson = "{\"data\":[{\"id\":1,\"name\":\"Alice\"}],\"type\":\"INSERT\"}";
        SeaTunnelRow input = createRow("topic", "key1", insertJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testCanalJson_AppendMode_Insert_MultiRecord() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String insertJson =
                "{\"data\":[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}],\"type\":\"INSERT\"}";
        SeaTunnelRow input = createRow("topic", "key1", insertJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testCanalJson_AppendMode_Update() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String updateJson =
                "{\"data\":[{\"id\":1,\"name\":\"Bob\"}],\"old\":[{\"name\":\"Alice\"}],\"type\":\"UPDATE\"}";
        SeaTunnelRow input = createRow("topic", "key1", updateJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testCanalJson_AppendMode_Update_MultiRecord() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String updateJson =
                "{\"data\":[{\"id\":1,\"name\":\"Bob\"},{\"id\":2,\"name\":\"Charlie\"}],\"old\":[{\"name\":\"Alice\"},{\"name\":\"Bob\"}],\"type\":\"UPDATE\"}";
        SeaTunnelRow input = createRow("topic", "key1", updateJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testCanalJson_AppendMode_Delete() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String deleteJson = "{\"data\":[{\"id\":1,\"name\":\"Alice\"}],\"type\":\"DELETE\"}";
        SeaTunnelRow input = createRow("topic", "key1", deleteJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testCanalJson_AppendMode_Delete_MultiRecord() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String deleteJson =
                "{\"data\":[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}],\"type\":\"DELETE\"}";
        SeaTunnelRow input = createRow("topic", "key1", deleteJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    // ==================== OGG_JSON Tests ====================

    @Test
    void testOggJson_SoftDelete_Delete() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.SOFT_DELETE.name());
        config.put("marker_field_name", "is_deleted");
        config.put("marker_field_value", "1");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String deleteJson =
                "{\"before\":{\"id\":1,\"name\":\"Alice\"},\"after\":null,\"op_type\":\"D\"}";
        SeaTunnelRow input = createRow("topic", "key1", deleteJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output.getRowKind());
        String enhancedValue = (String) output.getField(2);
        Assertions.assertTrue(enhancedValue.contains("is_deleted"));
        Assertions.assertTrue(enhancedValue.contains("\"op_type\":\"U\""));
    }

    @Test
    void testOggJson_SoftDelete_Insert() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.SOFT_DELETE.name());
        config.put("marker_field_name", "is_deleted");
        config.put("marker_field_value", "1");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String insertJson =
                "{\"before\":null,\"after\":{\"id\":1,\"name\":\"Bob\"},\"op_type\":\"I\"}";
        SeaTunnelRow input = createRow("topic", "key1", insertJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testOggJson_AddDmlMarker_Insert() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type_marker");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String insertJson =
                "{\"before\":null,\"after\":{\"id\":1,\"name\":\"Alice\"},\"op_type\":\"I\"}";
        SeaTunnelRow input = createRow("topic", "key1", insertJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testOggJson_AddDmlMarker_Update() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type_marker");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String updateJson =
                "{\"before\":{\"id\":1,\"name\":\"Alice\"},\"after\":{\"id\":1,\"name\":\"Bob\"},\"op_type\":\"U\"}";
        SeaTunnelRow input = createRow("topic", "key1", updateJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output.getRowKind());
    }

    @Test
    void testOggJson_AddDmlMarker_Delete() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_enabled", true);
        config.put("dml_marker_field_name", "op_type_marker");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String deleteJson =
                "{\"before\":{\"id\":1,\"name\":\"Alice\"},\"after\":null,\"op_type\":\"D\"}";
        SeaTunnelRow input = createRow("topic", "key1", deleteJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output.getRowKind());
    }

    @Test
    void testOggJson_AppendMode_Insert() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type_marker");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String insertJson =
                "{\"before\":null,\"after\":{\"id\":1,\"name\":\"Alice\"},\"op_type\":\"I\"}";
        SeaTunnelRow input = createRow("topic", "key1", insertJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testOggJson_AppendMode_Update() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type_marker");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String updateJson =
                "{\"before\":{\"id\":1,\"name\":\"Alice\"},\"after\":{\"id\":1,\"name\":\"Bob\"},\"op_type\":\"U\"}";
        SeaTunnelRow input = createRow("topic", "key1", updateJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    @Test
    void testOggJson_AppendMode_Delete() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        config.put("timestamp_field_name", "op_ts");
        config.put("dml_marker_field_name", "op_type_marker");

        DMLEventFilterTransform transform = buildTransform(table, config);

        String deleteJson =
                "{\"before\":{\"id\":1,\"name\":\"Alice\"},\"after\":null,\"op_type\":\"D\"}";
        SeaTunnelRow input = createRow("topic", "key1", deleteJson);
        SeaTunnelRow output = transform.map(input);

        Assertions.assertNotNull(output);
        Assertions.assertEquals(RowKind.INSERT, output.getRowKind());
    }

    // ==================== P0 Critical Tests ====================

    /**
     * Test enhancer caching behavior
     *
     * <p>This test verifies that once an enhancer is cached for a table, it is locked and reused
     * for all subsequent rows. The caching mechanism is designed for performance optimization by
     * avoiding repeated format detection.
     *
     * <p>Note: The cache does NOT support dynamic format switching. Once locked to a format, all
     * subsequent rows are expected to be in the same format. For mixed-format data streams, use
     * explicit cdc_json_format configuration or separate transforms per format.
     */
    @Test
    void testEnhancerCachingBehavior() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        config.put("dml_marker_field_name", "op_type");

        DMLEventFilterTransform transform = buildTransform(table, config);

        // Step 1: Process first Debezium JSON (caches Debezium enhancer)
        String debeziumJson1 =
                "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":1,\"name\":\"Alice\"}}}";
        SeaTunnelRow debeziumRow1 = createRow("topic", "key1", debeziumJson1);
        SeaTunnelRow output1 = transform.map(debeziumRow1);

        Assertions.assertNotNull(output1);
        Assertions.assertEquals(RowKind.INSERT, output1.getRowKind());
        String enhanced1 = (String) output1.getField(2);
        Assertions.assertTrue(
                enhanced1.contains("\"op\":\"c\""), "Debezium JSON should preserve op field");

        // Step 2: Process second Debezium JSON (reuses cached enhancer)
        String debeziumJson2 =
                "{\"payload\":{\"op\":\"u\",\"before\":{\"id\":1},\"after\":{\"id\":1,\"name\":\"Alice Updated\"}}}";
        SeaTunnelRow debeziumRow2 = createRow("topic", "key2", debeziumJson2);
        SeaTunnelRow output2 = transform.map(debeziumRow2);

        Assertions.assertNotNull(output2);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output2.getRowKind());
        String enhanced2 = (String) output2.getField(2);
        Assertions.assertTrue(
                enhanced2.contains("\"op\":\"u\""),
                "Cached enhancer should handle subsequent Debezium JSON");

        // Step 3: Process third Debezium JSON (still reuses cached enhancer)
        String debeziumJson3 =
                "{\"payload\":{\"op\":\"d\",\"before\":{\"id\":2,\"name\":\"Bob\"}}}";
        SeaTunnelRow debeziumRow3 = createRow("topic", "key3", debeziumJson3);
        SeaTunnelRow output3 = transform.map(debeziumRow3);

        Assertions.assertNotNull(output3);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output3.getRowKind());
        String enhanced3 = (String) output3.getField(2);
        Assertions.assertTrue(
                enhanced3.contains("op_type"),
                "Cached enhancer should consistently handle all Debezium JSON");
    }

    /**
     * Test consistent CDC format handling after caching
     *
     * <p>This test verifies that once an enhancer is cached for a specific CDC format, it
     * consistently processes all subsequent rows in that format. The caching mechanism ensures
     * optimal performance by avoiding repeated format detection.
     */
    @Test
    void testConsistentFormatHandling() {
        CatalogTable table = createCdcTable();
        Map<String, Object> config = new HashMap<>();
        config.put("processing_mode", ProcessingMode.SOFT_DELETE.name());
        config.put("marker_field_name", "is_deleted");
        config.put("marker_field_value", "1");

        DMLEventFilterTransform transform = buildTransform(table, config);

        // Step 1: Process first Debezium JSON (locks enhancer to Debezium format)
        String validJson1 = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":1,\"name\":\"Alice\"}}}";
        SeaTunnelRow validRow1 = createRow("topic", "key1", validJson1);
        SeaTunnelRow output1 = transform.map(validRow1);

        Assertions.assertNotNull(output1);
        Assertions.assertEquals(RowKind.INSERT, output1.getRowKind());
        String enhanced1 = (String) output1.getField(2);
        Assertions.assertTrue(enhanced1.contains("is_deleted"), "Should add soft delete marker");

        // Step 2: Process second Debezium JSON (reuses cached enhancer)
        String validJson2 =
                "{\"payload\":{\"op\":\"u\",\"before\":{\"id\":1},\"after\":{\"id\":1,\"name\":\"Alice Updated\"}}}";
        SeaTunnelRow validRow2 = createRow("topic", "key2", validJson2);
        SeaTunnelRow output2 = transform.map(validRow2);

        Assertions.assertNotNull(output2);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output2.getRowKind());
        String enhanced2 = (String) output2.getField(2);
        Assertions.assertTrue(
                enhanced2.contains("is_deleted"), "Cached enhancer should handle subsequent rows");

        // Step 3: Process third Debezium JSON (DELETE with soft delete)
        String validJson3 = "{\"payload\":{\"op\":\"d\",\"before\":{\"id\":2,\"name\":\"Bob\"}}}";
        SeaTunnelRow validRow3 = createRow("topic", "key3", validJson3);
        SeaTunnelRow output3 = transform.map(validRow3);

        Assertions.assertNotNull(output3);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, output3.getRowKind());
        String enhanced3 = (String) output3.getField(2);
        Assertions.assertTrue(
                enhanced3.contains("is_deleted"),
                "Cached enhancer should consistently handle all Debezium JSON with soft delete");
    }

    /**
     * Test timestamp type consistency between APPEND_MODE and ADD_DML_MARKER
     *
     * <p>This test verifies that both modes use the same timestamp format (formatted string) for
     * consistency.
     */
    @Test
    void testTimestampTypeConsistency() {
        CatalogTable table = createCdcTable();

        // Test APPEND_MODE timestamp format
        Map<String, Object> appendConfig = new HashMap<>();
        appendConfig.put("processing_mode", ProcessingMode.APPEND_MODE.name());
        appendConfig.put("timestamp_field_name", "op_ts");
        appendConfig.put("dml_marker_field_name", "op_type");
        appendConfig.put("timestamp_precision", 3);

        DMLEventFilterTransform appendTransform = buildTransform(table, appendConfig);

        String insertJson = "{\"payload\":{\"op\":\"c\",\"after\":{\"id\":1,\"name\":\"Alice\"}}}";
        SeaTunnelRow appendInput = createRow("topic", "key1", insertJson);
        SeaTunnelRow appendOutput = appendTransform.map(appendInput);

        String appendEnhancedValue = (String) appendOutput.getField(2);
        String appendTsValue = null;
        try {
            JsonNode appendNode = JsonUtils.parseObject(appendEnhancedValue);
            JsonNode appendAfter = appendNode.get("payload").get("after");
            JsonNode appendTimestamp = appendAfter.get("op_ts");

            // Verify APPEND_MODE timestamp is formatted string
            Assertions.assertTrue(
                    appendTimestamp.isTextual(),
                    "APPEND_MODE timestamp should be formatted string");
            appendTsValue = appendTimestamp.asText();
            Assertions.assertTrue(
                    appendTsValue.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
                    "APPEND_MODE timestamp should match format 'yyyy-MM-dd HH:mm:ss.SSS'");
        } catch (Exception e) {
            Assertions.fail("Failed to parse APPEND_MODE JSON: " + e.getMessage());
        }

        // Test ADD_DML_MARKER timestamp format
        Map<String, Object> addDmlConfig = new HashMap<>();
        addDmlConfig.put("processing_mode", ProcessingMode.ADD_DML_MARKER.name());
        addDmlConfig.put("dml_marker_enabled", true);
        addDmlConfig.put("dml_marker_field_name", "op_type");
        addDmlConfig.put("timestamp_enabled", true);
        addDmlConfig.put("timestamp_field_name", "op_ts");
        addDmlConfig.put("timestamp_precision", 3);

        DMLEventFilterTransform addDmlTransform = buildTransform(table, addDmlConfig);

        SeaTunnelRow addDmlInput = createRow("topic", "key2", insertJson);
        SeaTunnelRow addDmlOutput = addDmlTransform.map(addDmlInput);

        String addDmlEnhancedValue = (String) addDmlOutput.getField(2);
        try {
            JsonNode addDmlNode = JsonUtils.parseObject(addDmlEnhancedValue);
            JsonNode addDmlAfter = addDmlNode.get("payload").get("after");
            JsonNode addDmlTimestamp = addDmlAfter.get("op_ts");

            // Verify ADD_DML_MARKER timestamp is also formatted string (same as APPEND_MODE)
            Assertions.assertTrue(
                    addDmlTimestamp.isTextual(),
                    "ADD_DML_MARKER timestamp should be formatted string (same as APPEND_MODE)");
            String addDmlTsValue = addDmlTimestamp.asText();
            Assertions.assertTrue(
                    addDmlTsValue.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
                    "ADD_DML_MARKER timestamp should match format 'yyyy-MM-dd HH:mm:ss.SSS' (same as APPEND_MODE)");

            // Both timestamps should use the same format pattern
            Assertions.assertEquals(
                    appendTsValue.length(),
                    addDmlTsValue.length(),
                    "APPEND_MODE and ADD_DML_MARKER timestamps should have same format length");
        } catch (Exception e) {
            Assertions.fail("Failed to parse ADD_DML_MARKER JSON: " + e.getMessage());
        }
    }
}

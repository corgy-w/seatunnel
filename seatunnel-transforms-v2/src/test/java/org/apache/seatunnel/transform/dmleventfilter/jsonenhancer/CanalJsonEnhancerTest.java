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

package org.apache.seatunnel.transform.dmleventfilter.jsonenhancer;

import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.JsonNode;
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.seatunnel.api.table.type.RowKind;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class CanalJsonEnhancerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private CanalJsonEnhancer enhancer;

    @BeforeEach
    public void setUp() {
        enhancer = new CanalJsonEnhancer();
    }

    @Test
    public void testGetFormatName() {
        Assertions.assertEquals("CANAL_JSON", enhancer.getFormatName());
    }

    @Test
    public void testGetPriority() {
        Assertions.assertEquals(3, enhancer.getPriority());
    }

    @Test
    public void testCanHandleValidCanalJson() throws Exception {
        String json = "{\"data\": [{\"id\": 1, \"name\": \"Alice\"}], \"type\": \"INSERT\"}";
        JsonNode node = MAPPER.readTree(json);
        Assertions.assertTrue(enhancer.canHandle(node));
    }

    @Test
    public void testCanHandleInvalidJson() {
        JsonNode node = MAPPER.createObjectNode();
        Assertions.assertFalse(enhancer.canHandle(node));
    }

    @Test
    public void testCanHandleDebeziumJson() throws Exception {
        String json = "{\"payload\": {\"op\": \"c\", \"after\": {\"id\": 1}}}";
        JsonNode node = MAPPER.readTree(json);
        Assertions.assertFalse(enhancer.canHandle(node));
    }

    @Test
    public void testParseRowKindInsert() throws Exception {
        String json = "{\"data\": [{\"id\": 1}], \"type\": \"INSERT\"}";
        JsonNode node = MAPPER.readTree(json);
        Assertions.assertEquals(RowKind.INSERT, enhancer.parseRowKind(node));
    }

    @Test
    public void testParseRowKindUpdate() throws Exception {
        String json =
                "{\"data\": [{\"id\": 1, \"name\": \"Bob\"}], \"old\": [{\"name\": \"Alice\"}], \"type\": \"UPDATE\"}";
        JsonNode node = MAPPER.readTree(json);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, enhancer.parseRowKind(node));
    }

    @Test
    public void testParseRowKindDelete() throws Exception {
        String json = "{\"data\": [{\"id\": 1}], \"type\": \"DELETE\"}";
        JsonNode node = MAPPER.readTree(json);
        Assertions.assertEquals(RowKind.DELETE, enhancer.parseRowKind(node));
    }

    @Test
    public void testEnhanceSingleRecord() throws Exception {
        String json = "{\"data\": [{\"id\": 1, \"name\": \"Alice\"}], \"type\": \"INSERT\"}";
        JsonNode node = MAPPER.readTree(json);

        Map<String, Object> fieldsToAdd = new HashMap<>();
        fieldsToAdd.put("is_deleted", 0);
        fieldsToAdd.put("operation_type", "INSERT");

        JsonNode enhanced = enhancer.enhance(node, RowKind.INSERT, RowKind.INSERT, fieldsToAdd);

        JsonNode data = enhanced.get("data");
        Assertions.assertEquals(1, data.size());
        JsonNode record = data.get(0);
        Assertions.assertEquals(1, record.get("id").asInt());
        Assertions.assertEquals("Alice", record.get("name").asText());
        Assertions.assertEquals(0, record.get("is_deleted").asInt());
        Assertions.assertEquals("INSERT", record.get("operation_type").asText());
    }

    @Test
    public void testEnhanceMultipleRecords() throws Exception {
        String json =
                "{\"data\": [{\"id\": 1, \"name\": \"Alice\"}, {\"id\": 2, \"name\": \"Bob\"}], \"type\": \"INSERT\"}";
        JsonNode node = MAPPER.readTree(json);

        Map<String, Object> fieldsToAdd = new HashMap<>();
        fieldsToAdd.put("batch_id", 100);

        JsonNode enhanced = enhancer.enhance(node, RowKind.INSERT, RowKind.INSERT, fieldsToAdd);

        JsonNode data = enhanced.get("data");
        Assertions.assertEquals(2, data.size());

        JsonNode record1 = data.get(0);
        Assertions.assertEquals(1, record1.get("id").asInt());
        Assertions.assertEquals(100, record1.get("batch_id").asInt());

        JsonNode record2 = data.get(1);
        Assertions.assertEquals(2, record2.get("id").asInt());
        Assertions.assertEquals(100, record2.get("batch_id").asInt());
    }

    @Test
    public void testEnhanceDeleteToUpdate() throws Exception {
        String json = "{\"data\": [{\"id\": 1, \"name\": \"Alice\"}], \"type\": \"DELETE\"}";
        JsonNode node = MAPPER.readTree(json);

        Map<String, Object> fieldsToAdd = new HashMap<>();
        fieldsToAdd.put("is_deleted", 1);

        JsonNode enhanced =
                enhancer.enhance(node, RowKind.DELETE, RowKind.UPDATE_AFTER, fieldsToAdd);

        // Check type changed from "DELETE" to "UPDATE"
        Assertions.assertEquals("UPDATE", enhanced.get("type").asText());

        // Check field added to data
        JsonNode data = enhanced.get("data");
        JsonNode record = data.get(0);
        Assertions.assertEquals(1, record.get("is_deleted").asInt());
    }

    @Test
    public void testEnhanceUpdateWithOld() throws Exception {
        String json =
                "{\"data\": [{\"id\": 1, \"name\": \"Bob\"}], \"old\": [{\"name\": \"Alice\"}], \"type\": \"UPDATE\"}";
        JsonNode node = MAPPER.readTree(json);

        Map<String, Object> fieldsToAdd = new HashMap<>();
        fieldsToAdd.put("updated_at", "2024-01-01");

        JsonNode enhanced =
                enhancer.enhance(node, RowKind.UPDATE_AFTER, RowKind.UPDATE_AFTER, fieldsToAdd);

        // Check field added to data
        JsonNode data = enhanced.get("data");
        Assertions.assertEquals("2024-01-01", data.get(0).get("updated_at").asText());

        // Check field also added to old
        JsonNode old = enhanced.get("old");
        Assertions.assertEquals("2024-01-01", old.get(0).get("updated_at").asText());
    }

    @Test
    public void testEnhanceInvalidJson() {
        JsonNode node = MAPPER.createObjectNode();

        Map<String, Object> fieldsToAdd = new HashMap<>();
        fieldsToAdd.put("test", "value");

        Assertions.assertThrows(
                CdcJsonEnhanceException.class,
                () -> enhancer.enhance(node, RowKind.INSERT, RowKind.INSERT, fieldsToAdd));
    }

    @Test
    public void testEnhanceEmptyDataArray() throws Exception {
        String json = "{\"data\": [], \"type\": \"INSERT\"}";
        JsonNode node = MAPPER.readTree(json);

        Map<String, Object> fieldsToAdd = new HashMap<>();
        fieldsToAdd.put("marker", "test");

        JsonNode enhanced = enhancer.enhance(node, RowKind.INSERT, RowKind.INSERT, fieldsToAdd);

        // Verify data array is still empty (no records to enhance)
        JsonNode data = enhanced.get("data");
        Assertions.assertTrue(data.isArray());
        Assertions.assertEquals(0, data.size());
    }

    @Test
    public void testEnhanceWithNullFieldValue() throws Exception {
        String json = "{\"data\": [{\"id\": 1, \"name\": null}], \"type\": \"INSERT\"}";
        JsonNode node = MAPPER.readTree(json);

        Map<String, Object> fieldsToAdd = new HashMap<>();
        fieldsToAdd.put("marker", null);
        fieldsToAdd.put("flag", 1);

        JsonNode enhanced = enhancer.enhance(node, RowKind.INSERT, RowKind.INSERT, fieldsToAdd);

        JsonNode record = enhanced.get("data").get(0);

        // Verify null field is added as null
        Assertions.assertTrue(record.has("marker"));
        Assertions.assertTrue(record.get("marker").isNull());

        // Verify non-null field is added correctly
        Assertions.assertEquals(1, record.get("flag").asInt());

        // Verify original null field is preserved
        Assertions.assertTrue(record.has("name"));
        Assertions.assertTrue(record.get("name").isNull());
    }
}

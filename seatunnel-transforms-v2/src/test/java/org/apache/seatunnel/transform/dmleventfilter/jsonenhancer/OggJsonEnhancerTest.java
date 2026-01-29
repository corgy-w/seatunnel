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

public class OggJsonEnhancerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private OggJsonEnhancer enhancer;

    @BeforeEach
    public void setUp() {
        enhancer = new OggJsonEnhancer();
    }

    @Test
    public void testGetFormatName() {
        Assertions.assertEquals("OGG_JSON", enhancer.getFormatName());
    }

    @Test
    public void testGetPriority() {
        Assertions.assertEquals(2, enhancer.getPriority());
    }

    @Test
    public void testCanHandleValidOggJson() throws Exception {
        String json =
                "{\"before\": null, \"after\": {\"id\": 1, \"name\": \"Alice\"}, \"op_type\": \"I\"}";
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
        String json = "{\"after\": {\"id\": 1}, \"op_type\": \"I\"}";
        JsonNode node = MAPPER.readTree(json);
        Assertions.assertEquals(RowKind.INSERT, enhancer.parseRowKind(node));
    }

    @Test
    public void testParseRowKindUpdate() throws Exception {
        String json =
                "{\"before\": {\"id\": 1, \"name\": \"Alice\"}, \"after\": {\"id\": 1, \"name\": \"Bob\"}, \"op_type\": \"U\"}";
        JsonNode node = MAPPER.readTree(json);
        Assertions.assertEquals(RowKind.UPDATE_AFTER, enhancer.parseRowKind(node));
    }

    @Test
    public void testParseRowKindDelete() throws Exception {
        String json = "{\"before\": {\"id\": 1}, \"after\": null, \"op_type\": \"D\"}";
        JsonNode node = MAPPER.readTree(json);
        Assertions.assertEquals(RowKind.DELETE, enhancer.parseRowKind(node));
    }

    @Test
    public void testEnhanceInsert() throws Exception {
        String json =
                "{\"before\": null, \"after\": {\"id\": 1, \"name\": \"Alice\"}, \"op_type\": \"I\"}";
        JsonNode node = MAPPER.readTree(json);

        Map<String, Object> fieldsToAdd = new HashMap<>();
        fieldsToAdd.put("is_deleted", 0);
        fieldsToAdd.put("operation_type", "INSERT");

        JsonNode enhanced = enhancer.enhance(node, RowKind.INSERT, RowKind.INSERT, fieldsToAdd);

        JsonNode after = enhanced.get("after");
        Assertions.assertEquals(1, after.get("id").asInt());
        Assertions.assertEquals("Alice", after.get("name").asText());
        Assertions.assertEquals(0, after.get("is_deleted").asInt());
        Assertions.assertEquals("INSERT", after.get("operation_type").asText());
    }

    @Test
    public void testEnhanceDelete() throws Exception {
        String json =
                "{\"before\": {\"id\": 1, \"name\": \"Alice\"}, \"after\": null, \"op_type\": \"D\"}";
        JsonNode node = MAPPER.readTree(json);

        Map<String, Object> fieldsToAdd = new HashMap<>();
        fieldsToAdd.put("is_deleted", 1);

        JsonNode enhanced = enhancer.enhance(node, RowKind.DELETE, RowKind.DELETE, fieldsToAdd);

        JsonNode before = enhanced.get("before");
        Assertions.assertEquals(1, before.get("id").asInt());
        Assertions.assertEquals("Alice", before.get("name").asText());
        Assertions.assertEquals(1, before.get("is_deleted").asInt());
    }

    @Test
    public void testEnhanceDeleteToUpdate() throws Exception {
        String json =
                "{\"before\": {\"id\": 1, \"name\": \"Alice\"}, \"after\": null, \"op_type\": \"D\"}";
        JsonNode node = MAPPER.readTree(json);

        Map<String, Object> fieldsToAdd = new HashMap<>();
        fieldsToAdd.put("is_deleted", 1);

        JsonNode enhanced =
                enhancer.enhance(node, RowKind.DELETE, RowKind.UPDATE_AFTER, fieldsToAdd);

        // Check op_type changed from "D" to "U"
        Assertions.assertEquals("U", enhanced.get("op_type").asText());

        // Check field added to after (when converting DELETE to UPDATE_AFTER)
        JsonNode after = enhanced.get("after");
        Assertions.assertNotNull(
                after, "After should not be null when converting DELETE to UPDATE_AFTER");
        Assertions.assertEquals(1, after.get("is_deleted").asInt());

        // Verify before is still present with original data
        JsonNode before = enhanced.get("before");
        Assertions.assertNotNull(before, "Before should not be null");
        Assertions.assertEquals(1, before.get("id").asInt());
        Assertions.assertEquals("Alice", before.get("name").asText());
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
}

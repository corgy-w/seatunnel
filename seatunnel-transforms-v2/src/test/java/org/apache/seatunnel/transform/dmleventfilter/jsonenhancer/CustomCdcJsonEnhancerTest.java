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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class CustomCdcJsonEnhancerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private CustomCdcJsonEnhancer enhancer;

    @BeforeEach
    public void setUp() {
        Map<String, RowKind> mapping = new HashMap<>();
        mapping.put("I", RowKind.INSERT);
        mapping.put("U", RowKind.UPDATE_AFTER);
        mapping.put("D", RowKind.DELETE);

        EnumMap<RowKind, String> reverse = new EnumMap<>(RowKind.class);
        reverse.put(RowKind.INSERT, "I");
        reverse.put(RowKind.UPDATE_AFTER, "U");
        reverse.put(RowKind.DELETE, "D");

        enhancer = new CustomCdcJsonEnhancer(new CustomCdcConfig("op", "record", mapping, reverse));
    }

    @Test
    public void testCanHandle() throws Exception {
        JsonNode node = MAPPER.readTree("{\"op\":\"I\",\"record\":{\"id\":1}}");
        Assertions.assertTrue(enhancer.canHandle(node));
    }

    @Test
    public void testParseRowKind() throws Exception {
        JsonNode node = MAPPER.readTree("{\"op\":\"U\",\"record\":{\"id\":1}}");
        Assertions.assertEquals(RowKind.UPDATE_AFTER, enhancer.parseRowKind(node));
    }

    @Test
    public void testEnhanceObjectPayload() throws Exception {
        JsonNode node = MAPPER.readTree("{\"op\":\"D\",\"record\":{\"id\":1}}");
        Map<String, Object> fields = new HashMap<>();
        fields.put("flag", "Y");

        JsonNode enhanced = enhancer.enhance(node, RowKind.DELETE, RowKind.UPDATE_AFTER, fields);
        Assertions.assertEquals("U", enhanced.get("op").asText());
        Assertions.assertEquals("Y", enhanced.get("record").get("flag").asText());
    }

    @Test
    public void testEnhanceArrayPayload() throws Exception {
        JsonNode node = MAPPER.readTree("{\"op\":\"D\",\"record\":[{\"id\":1},{\"id\":2}]}");
        Map<String, Object> fields = new HashMap<>();
        fields.put("flag", "Y");

        JsonNode enhanced = enhancer.enhance(node, RowKind.DELETE, RowKind.UPDATE_AFTER, fields);
        Assertions.assertEquals("U", enhanced.get("op").asText());
        Assertions.assertEquals("Y", enhanced.get("record").get(0).get("flag").asText());
    }

    @Test
    public void testDualFieldModeDeleteToUpdateAfterWithAfterField() throws Exception {
        // Setup dual-field mode config (like real Custom CDC format)
        Map<String, RowKind> mapping = new HashMap<>();
        mapping.put("I", RowKind.INSERT);
        mapping.put("U", RowKind.UPDATE_AFTER);
        mapping.put("D", RowKind.DELETE);

        EnumMap<RowKind, String> reverse = new EnumMap<>(RowKind.class);
        reverse.put(RowKind.INSERT, "I");
        reverse.put(RowKind.UPDATE_AFTER, "U");
        reverse.put(RowKind.DELETE, "D");

        // Use new dual-field constructor
        CustomCdcConfig dualFieldConfig =
                new CustomCdcConfig("op_type", "before", "after", mapping, reverse);
        CustomCdcJsonEnhancer dualFieldEnhancer = new CustomCdcJsonEnhancer(dualFieldConfig);

        // Create DELETE event (has before only)
        String deleteJson =
                "{"
                        + "\"table\":\"test.users\","
                        + "\"op_type\":\"D\","
                        + "\"before\":{\"id\":1,\"name\":\"test\"}"
                        + "}";
        JsonNode deleteNode = MAPPER.readTree(deleteJson);

        // Add marker field for soft delete
        Map<String, Object> markerFields = new HashMap<>();
        markerFields.put("is_deleted", "1");

        // Enhance: DELETE -> UPDATE_AFTER
        JsonNode enhanced =
                dualFieldEnhancer.enhance(
                        deleteNode, RowKind.DELETE, RowKind.UPDATE_AFTER, markerFields);

        // Verify results
        Assertions.assertEquals("U", enhanced.get("op_type").asText(), "op_type should be U");
        Assertions.assertNotNull(enhanced.get("before"), "before should exist");
        Assertions.assertNotNull(enhanced.get("after"), "after should be created");

        // Verify before is unchanged
        Assertions.assertEquals(1, enhanced.get("before").get("id").asInt());
        Assertions.assertEquals("test", enhanced.get("before").get("name").asText());

        // Verify after has marker field
        Assertions.assertEquals(1, enhanced.get("after").get("id").asInt());
        Assertions.assertEquals("test", enhanced.get("after").get("name").asText());
        Assertions.assertEquals(
                "1",
                enhanced.get("after").get("is_deleted").asText(),
                "after should have is_deleted marker");
    }

    @Test
    public void testDualFieldModeInsert() throws Exception {
        Map<String, RowKind> mapping = new HashMap<>();
        mapping.put("I", RowKind.INSERT);
        mapping.put("U", RowKind.UPDATE_AFTER);
        mapping.put("D", RowKind.DELETE);

        EnumMap<RowKind, String> reverse = new EnumMap<>(RowKind.class);
        reverse.put(RowKind.INSERT, "I");
        reverse.put(RowKind.UPDATE_AFTER, "U");
        reverse.put(RowKind.DELETE, "D");

        CustomCdcConfig dualFieldConfig =
                new CustomCdcConfig("op_type", "before", "after", mapping, reverse);
        CustomCdcJsonEnhancer dualFieldEnhancer = new CustomCdcJsonEnhancer(dualFieldConfig);

        // Create INSERT event (has after only)
        String insertJson =
                "{" + "\"op_type\":\"I\"," + "\"after\":{\"id\":2,\"name\":\"new\"}" + "}";
        JsonNode insertNode = MAPPER.readTree(insertJson);

        Map<String, Object> fields = new HashMap<>();
        fields.put("created_by", "system");

        JsonNode enhanced =
                dualFieldEnhancer.enhance(insertNode, RowKind.INSERT, RowKind.INSERT, fields);

        Assertions.assertEquals("I", enhanced.get("op_type").asText());
        Assertions.assertEquals("system", enhanced.get("after").get("created_by").asText());
    }

    @Test
    public void testDualFieldModeUpdate() throws Exception {
        Map<String, RowKind> mapping = new HashMap<>();
        mapping.put("I", RowKind.INSERT);
        mapping.put("U", RowKind.UPDATE_AFTER);
        mapping.put("D", RowKind.DELETE);

        EnumMap<RowKind, String> reverse = new EnumMap<>(RowKind.class);
        reverse.put(RowKind.INSERT, "I");
        reverse.put(RowKind.UPDATE_AFTER, "U");
        reverse.put(RowKind.DELETE, "D");

        CustomCdcConfig dualFieldConfig =
                new CustomCdcConfig("op_type", "before", "after", mapping, reverse);
        CustomCdcJsonEnhancer dualFieldEnhancer = new CustomCdcJsonEnhancer(dualFieldConfig);

        // Create UPDATE event (has both before and after)
        String updateJson =
                "{"
                        + "\"op_type\":\"U\","
                        + "\"before\":{\"id\":1,\"name\":\"old\"},"
                        + "\"after\":{\"id\":1,\"name\":\"new\"}"
                        + "}";
        JsonNode updateNode = MAPPER.readTree(updateJson);

        Map<String, Object> fields = new HashMap<>();
        fields.put("updated_by", "admin");

        JsonNode enhanced =
                dualFieldEnhancer.enhance(
                        updateNode, RowKind.UPDATE_AFTER, RowKind.UPDATE_AFTER, fields);

        Assertions.assertEquals("U", enhanced.get("op_type").asText());
        Assertions.assertEquals("admin", enhanced.get("after").get("updated_by").asText());
    }
}

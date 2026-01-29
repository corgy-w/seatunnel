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
import java.util.List;

public class CdcJsonEnhancerManagerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private CdcJsonEnhancerManager manager;

    @BeforeEach
    public void setUp() {
        manager = CdcJsonEnhancerManager.createForTest();
    }

    @Test
    public void testGetEnhancers() {
        List<ICdcJsonEnhancer> enhancers = manager.getEnhancers();
        Assertions.assertEquals(6, enhancers.size());

        // Verify formats
        Assertions.assertEquals("DEBEZIUM_JSON", enhancers.get(0).getFormatName());
        Assertions.assertEquals("COMPATIBLE_DEBEZIUM_JSON", enhancers.get(1).getFormatName());
        Assertions.assertEquals("OGG_JSON", enhancers.get(2).getFormatName());
        Assertions.assertEquals("KINGBASE_JSON", enhancers.get(3).getFormatName());
        Assertions.assertEquals("CANAL_JSON", enhancers.get(4).getFormatName());
        Assertions.assertEquals("CUSTOM_JSON", enhancers.get(5).getFormatName());

        // Verify priority order (lower number = higher priority)
        for (int i = 0; i < enhancers.size() - 1; i++) {
            Assertions.assertTrue(
                    enhancers.get(i).getPriority() <= enhancers.get(i + 1).getPriority());
        }
    }

    @Test
    public void testDetectDebeziumJson() throws Exception {
        String json = "{\"payload\": {\"op\": \"c\", \"after\": {\"id\": 1}}}";
        JsonNode node = MAPPER.readTree(json);

        ICdcJsonEnhancer enhancer = manager.detectEnhancer(node);
        Assertions.assertNotNull(enhancer);
        Assertions.assertEquals("DEBEZIUM_JSON", enhancer.getFormatName());
    }

    @Test
    public void testDetectCompatibleDebeziumJson() throws Exception {
        String innerJson = "{\"payload\": {\"op\": \"c\", \"after\": {\"id\": 1}}}";
        String json =
                String.format(
                        "{\"topic\": \"test\", \"key\": \"1\", \"value\": \"%s\"}",
                        innerJson.replace("\"", "\\\""));
        JsonNode node = MAPPER.readTree(json);

        ICdcJsonEnhancer enhancer = manager.detectEnhancer(node);
        Assertions.assertNotNull(enhancer);
        Assertions.assertEquals("COMPATIBLE_DEBEZIUM_JSON", enhancer.getFormatName());
    }

    @Test
    public void testDetectCanalJson() throws Exception {
        String json = "{\"data\": [{\"id\": 1}], \"type\": \"INSERT\"}";
        JsonNode node = MAPPER.readTree(json);

        ICdcJsonEnhancer enhancer = manager.detectEnhancer(node);
        Assertions.assertNotNull(enhancer);
        Assertions.assertEquals("CANAL_JSON", enhancer.getFormatName());
    }

    @Test
    public void testDetectOggJson() throws Exception {
        String json = "{\"before\": null, \"after\": {\"id\": 1}, \"op_type\": \"I\"}";
        JsonNode node = MAPPER.readTree(json);

        ICdcJsonEnhancer enhancer = manager.detectEnhancer(node);
        Assertions.assertNotNull(enhancer);
        Assertions.assertEquals("OGG_JSON", enhancer.getFormatName());
    }

    @Test
    public void testDetectKingbaseJson() throws Exception {
        String json =
                "{\"data\": [{\"id\": 1}], \"type\": \"DELETE\", \"database\":\"db\",\"schema\":\"public\"}";
        JsonNode node = MAPPER.readTree(json);

        ICdcJsonEnhancer enhancer = manager.detectEnhancer(node);
        Assertions.assertNotNull(enhancer);
        Assertions.assertEquals("KINGBASE_JSON", enhancer.getFormatName());
    }

    @Test
    public void testDetectUnknownFormat() throws Exception {
        String json = "{\"unknown\": \"format\"}";
        JsonNode node = MAPPER.readTree(json);

        ICdcJsonEnhancer enhancer = manager.detectEnhancer(node);
        Assertions.assertNull(enhancer);
    }

    @Test
    public void testDetectNullNode() {
        ICdcJsonEnhancer enhancer = manager.detectEnhancer(null);
        Assertions.assertNull(enhancer);
    }

    @Test
    public void testCustomEnhancerRegistration() throws Exception {
        HashMap<String, RowKind> mapping = new HashMap<>();
        mapping.put("I", RowKind.INSERT);
        mapping.put("U", RowKind.UPDATE_AFTER);
        mapping.put("D", RowKind.DELETE);

        EnumMap<RowKind, String> reverse = new EnumMap<>(RowKind.class);
        reverse.put(RowKind.INSERT, "I");
        reverse.put(RowKind.UPDATE_AFTER, "U");
        reverse.put(RowKind.DELETE, "D");

        CustomCdcConfig customConfig = new CustomCdcConfig("op_type", "record", mapping, reverse);
        CdcJsonEnhancerManager customManager = CdcJsonEnhancerManager.createForTest();
        customManager.registerCustomEnhancer(customConfig);

        String json = "{\"op_type\":\"I\",\"record\":{\"id\":1}}";
        JsonNode node = MAPPER.readTree(json);

        ICdcJsonEnhancer enhancer = customManager.detectEnhancer(node);
        Assertions.assertNotNull(enhancer);
        Assertions.assertEquals("CUSTOM_CDC_JSON", enhancer.getFormatName());
        Assertions.assertEquals(RowKind.INSERT, enhancer.parseRowKind(node));
    }
}

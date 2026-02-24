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
import org.junit.jupiter.api.Test;

import java.util.HashMap;

public class KingbaseJsonEnhancerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final KingbaseJsonEnhancer enhancer = new KingbaseJsonEnhancer();

    @Test
    public void testCanHandle() throws Exception {
        JsonNode node =
                MAPPER.readTree(
                        "{\"data\":[{\"id\":1}],\"type\":\"INSERT\",\"database\":\"db\",\"schema\":\"public\"}");
        Assertions.assertTrue(enhancer.canHandle(node));
    }

    @Test
    public void testParseRowKind() throws Exception {
        JsonNode insertNode =
                MAPPER.readTree(
                        "{\"data\":[{\"id\":1}],\"type\":\"INSERT\",\"database\":\"db\",\"schema\":\"public\"}");
        Assertions.assertEquals(RowKind.INSERT, enhancer.parseRowKind(insertNode));

        JsonNode deleteNode =
                MAPPER.readTree(
                        "{\"data\":[{\"id\":1}],\"type\":\"DELETE\",\"database\":\"db\",\"schema\":\"public\"}");
        Assertions.assertEquals(RowKind.DELETE, enhancer.parseRowKind(deleteNode));
    }

    @Test
    public void testEnhance() throws Exception {
        JsonNode node =
                MAPPER.readTree(
                        "{\"data\":[{\"id\":1}],\"type\":\"DELETE\",\"database\":\"db\",\"schema\":\"public\"}");
        HashMap<String, Object> fields = new HashMap<>();
        fields.put("is_deleted", "Y");

        JsonNode enhanced = enhancer.enhance(node, RowKind.DELETE, RowKind.UPDATE_AFTER, fields);
        Assertions.assertEquals("UPDATE", enhanced.get("type").asText());
        Assertions.assertEquals("Y", enhanced.get("data").get(0).get("is_deleted").asText());
    }
}

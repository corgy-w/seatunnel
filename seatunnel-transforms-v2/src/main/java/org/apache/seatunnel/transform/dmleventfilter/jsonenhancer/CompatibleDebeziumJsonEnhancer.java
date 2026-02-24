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

import org.apache.seatunnel.shade.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.JsonNode;
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.seatunnel.api.table.type.RowKind;

import java.util.Map;

/**
 * Compatible Debezium JSON format enhancer
 *
 * <p>Handles Compatible Debezium JSON format (Kafka topic/key/value structure) with structure: {
 * "topic": "...", "key": "...", "value": "{...debezium json string...}" }
 *
 * <p>The "value" field contains a JSON string of standard Debezium format.
 */
public class CompatibleDebeziumJsonEnhancer extends AbstractCdcJsonEnhancer
        implements IUpdateSplittableEnhancer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final DebeziumJsonEnhancer debeziumEnhancer = new DebeziumJsonEnhancer();

    @Override
    public String getFormatName() {
        return "COMPATIBLE_DEBEZIUM_JSON";
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public boolean canHandle(JsonNode valueNode) {
        JsonNode innerJson = parseInnerDebeziumJsonIfCompatibleOrNull(valueNode);
        return innerJson != null && debeziumEnhancer.canHandle(innerJson);
    }

    @Override
    public RowKind parseRowKind(JsonNode valueNode) {
        JsonNode innerJson = parseInnerDebeziumJsonIfCompatibleOrNull(valueNode);
        if (innerJson == null || !debeziumEnhancer.canHandle(innerJson)) {
            return null;
        }
        return debeziumEnhancer.parseRowKind(innerJson);
    }

    @Override
    public JsonNode enhance(
            JsonNode valueNode,
            RowKind originalRowKind,
            RowKind targetRowKind,
            Map<String, Object> fieldsToAdd)
            throws CdcJsonEnhanceException {
        JsonNode innerJson = parseInnerDebeziumJsonIfCompatibleOrNull(valueNode);
        if (innerJson == null || !debeziumEnhancer.canHandle(innerJson)) {
            throw new CdcJsonEnhanceException(
                    "Cannot enhance non-Compatible-Debezium JSON: " + valueNode);
        }

        try {
            ObjectNode root = (ObjectNode) valueNode;

            // Enhance inner Debezium JSON
            JsonNode enhancedInner =
                    debeziumEnhancer.enhance(
                            innerJson, originalRowKind, targetRowKind, fieldsToAdd);

            // Serialize back to string and update value field
            String enhancedValueStr = OBJECT_MAPPER.writeValueAsString(enhancedInner);
            root.put("value", enhancedValueStr);

            return root;
        } catch (JsonProcessingException e) {
            throw new CdcJsonEnhanceException("Failed to enhance Compatible Debezium JSON", e);
        }
    }

    @Override
    public JsonNode getPayload(JsonNode valueNode) {
        JsonNode inner = parseInnerDebeziumJsonIfCompatibleOrNull(valueNode);
        if (inner == null || !inner.isObject()) {
            return null;
        }
        return inner.get("payload");
    }

    @Override
    public JsonNode buildPayload(JsonNode before, JsonNode after, String op) {
        return debeziumEnhancer.buildPayload(before, after, op);
    }

    @Override
    public JsonNode replacePayload(JsonNode originalNode, JsonNode newPayload)
            throws CdcJsonEnhanceException {
        if (originalNode == null || !originalNode.isObject()) {
            throw new CdcJsonEnhanceException(
                    "Original node is null or not an object: " + originalNode);
        }
        if (newPayload == null || !newPayload.isObject()) {
            throw new CdcJsonEnhanceException(
                    "New payload is null or not an object: " + newPayload);
        }

        ObjectNode result = ((ObjectNode) originalNode).deepCopy();

        JsonNode value = result.get("value");
        if (value == null || !value.isTextual()) {
            throw new CdcJsonEnhanceException(
                    "Compatible Debezium JSON missing textual 'value' field: " + originalNode);
        }

        try {
            JsonNode inner = OBJECT_MAPPER.readTree(value.asText());
            if (inner == null || !inner.isObject()) {
                throw new CdcJsonEnhanceException(
                        "Compatible Debezium JSON inner value is not an object: " + inner);
            }
            JsonNode updatedInner = debeziumEnhancer.replacePayload(inner, newPayload);
            result.put("value", OBJECT_MAPPER.writeValueAsString(updatedInner));
            return result;
        } catch (JsonProcessingException e) {
            throw new CdcJsonEnhanceException("Failed to replace inner Debezium payload", e);
        }
    }

    private JsonNode parseInnerDebeziumJsonIfCompatibleOrNull(JsonNode valueNode) {
        if (valueNode == null || !valueNode.isObject()) {
            return null;
        }

        JsonNode topic = valueNode.get("topic");
        JsonNode key = valueNode.get("key");
        JsonNode value = valueNode.get("value");
        if (topic == null || key == null || value == null || !value.isTextual()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(value.asText());
        } catch (Exception e) {
            return null;
        }
    }
}

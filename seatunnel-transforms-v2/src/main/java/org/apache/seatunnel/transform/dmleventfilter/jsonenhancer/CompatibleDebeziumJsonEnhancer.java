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
    private final DebeziumSimpleJsonEnhancer simpleEnhancer = new DebeziumSimpleJsonEnhancer();

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
        // Support both standard Debezium (with payload) and Debezium simple (no payload wrapper).
        return innerJson != null
                && (debeziumEnhancer.canHandle(innerJson) || simpleEnhancer.canHandle(innerJson));
    }

    @Override
    public RowKind parseRowKind(JsonNode valueNode) {
        return delegateToEnhancer(valueNode, (enhancer, node) -> enhancer.parseRowKind(node), null);
    }

    @Override
    public JsonNode enhance(
            JsonNode valueNode,
            RowKind originalRowKind,
            RowKind targetRowKind,
            Map<String, Object> fieldsToAdd)
            throws CdcJsonEnhanceException {
        JsonNode innerJson = parseInnerDebeziumJsonIfCompatibleOrNull(valueNode);
        if (innerJson != null) {
            try {
                ObjectNode root = (ObjectNode) valueNode;

                JsonNode enhancedInner;
                if (debeziumEnhancer.canHandle(innerJson)) {
                    enhancedInner =
                            debeziumEnhancer.enhance(
                                    innerJson, originalRowKind, targetRowKind, fieldsToAdd);
                } else if (simpleEnhancer.canHandle(innerJson)) {
                    enhancedInner =
                            simpleEnhancer.enhance(
                                    innerJson, originalRowKind, targetRowKind, fieldsToAdd);
                } else {
                    throw new CdcJsonEnhanceException(
                            "Inner JSON is neither standard nor simple Debezium format: "
                                    + innerJson);
                }

                String enhancedValueStr = OBJECT_MAPPER.writeValueAsString(enhancedInner);
                root.put("value", enhancedValueStr);
                return root;
            } catch (JsonProcessingException e) {
                throw new CdcJsonEnhanceException("Failed to enhance Compatible Debezium JSON", e);
            }
        }

        // Some pipelines may pass the inner Debezium JSON directly (no topic/key/value wrapper).
        if (debeziumEnhancer.canHandle(valueNode)) {
            return debeziumEnhancer.enhance(valueNode, originalRowKind, targetRowKind, fieldsToAdd);
        }
        if (simpleEnhancer.canHandle(valueNode)) {
            return simpleEnhancer.enhance(valueNode, originalRowKind, targetRowKind, fieldsToAdd);
        }

        throw new CdcJsonEnhanceException(
                "Cannot enhance non-Compatible-Debezium JSON: " + valueNode);
    }

    @Override
    public JsonNode getPayload(JsonNode valueNode) {
        // Try inner JSON first (wrapped format)
        JsonNode innerJson = parseInnerDebeziumJsonIfCompatibleOrNull(valueNode);
        if (innerJson != null) {
            JsonNode result = tryGetPayload(innerJson);
            if (result != null) {
                return result;
            }
        }

        // Fallback: try direct valueNode (unwrapped format)
        return tryGetPayload(valueNode);
    }

    private JsonNode tryGetPayload(JsonNode node) {
        if (debeziumEnhancer.canHandle(node)) {
            return debeziumEnhancer.getPayload(node);
        }
        if (simpleEnhancer.canHandle(node)) {
            return simpleEnhancer.getPayload(node);
        }
        return null;
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

        // Wrapper format: {"topic": "...", "key": "...", "value": "{...inner...}"}.
        JsonNode value = result.get("value");
        if (value != null && value.isTextual()) {
            try {
                JsonNode inner = OBJECT_MAPPER.readTree(value.asText());
                if (inner == null || !inner.isObject()) {
                    throw new CdcJsonEnhanceException(
                            "Compatible Debezium JSON inner value is not an object: " + inner);
                }
                JsonNode updatedInner;
                if (debeziumEnhancer.canHandle(inner)) {
                    updatedInner = debeziumEnhancer.replacePayload(inner, newPayload);
                } else if (simpleEnhancer.canHandle(inner)) {
                    updatedInner = simpleEnhancer.replacePayload(inner, newPayload);
                } else {
                    throw new CdcJsonEnhanceException(
                            "Inner JSON is neither standard nor simple Debezium format: " + inner);
                }
                result.put("value", OBJECT_MAPPER.writeValueAsString(updatedInner));
                return result;
            } catch (JsonProcessingException e) {
                throw new CdcJsonEnhanceException("Failed to replace inner Debezium payload", e);
            }
        }

        // Some pipelines may pass the inner Debezium JSON directly (no wrapper).
        if (debeziumEnhancer.canHandle(result)) {
            return debeziumEnhancer.replacePayload(result, newPayload);
        }
        if (simpleEnhancer.canHandle(result)) {
            return simpleEnhancer.replacePayload(result, newPayload);
        }
        if (value != null) {
            throw new CdcJsonEnhanceException(
                    "Compatible Debezium JSON missing textual 'value' field: " + originalNode);
        }
        throw new CdcJsonEnhanceException(
                "Cannot replace payload for non-Debezium JSON: " + originalNode);
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

    /** Functional interface for delegating operations to an enhancer. */
    @FunctionalInterface
    private interface EnhancerOperation<T> {
        T apply(ICdcJsonEnhancer enhancer, JsonNode node) throws CdcJsonEnhanceException;
    }

    /**
     * Delegates an operation to the appropriate enhancer (standard or simple Debezium). Tries
     * innerJson first, then falls back to direct valueNode.
     */
    private <T> T delegateToEnhancer(
            JsonNode valueNode, EnhancerOperation<T> operation, T defaultValue)
            throws CdcJsonEnhanceException {

        // Try inner JSON first (wrapped format)
        JsonNode innerJson = parseInnerDebeziumJsonIfCompatibleOrNull(valueNode);
        if (innerJson != null) {
            T result = tryEnhancers(innerJson, operation);
            if (result != null || defaultValue == null) {
                return result;
            }
        }

        // Fallback: try direct valueNode (unwrapped format)
        return tryEnhancers(valueNode, operation, defaultValue);
    }

    /** Tries both debezium and simple enhancers in order. */
    private <T> T tryEnhancers(JsonNode node, EnhancerOperation<T> operation)
            throws CdcJsonEnhanceException {
        return tryEnhancers(node, operation, null);
    }

    /** Tries both debezium and simple enhancers in order, with default value. */
    private <T> T tryEnhancers(JsonNode node, EnhancerOperation<T> operation, T defaultValue)
            throws CdcJsonEnhanceException {
        if (debeziumEnhancer.canHandle(node)) {
            return operation.apply(debeziumEnhancer, node);
        }
        if (simpleEnhancer.canHandle(node)) {
            return operation.apply(simpleEnhancer, node);
        }
        return defaultValue;
    }
}

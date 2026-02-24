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
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.seatunnel.api.table.type.RowKind;

import java.util.Map;

/**
 * Custom JSON format enhancer
 *
 * <p>Handles custom CDC JSON format (see me/custom_json.txt) with structure: { "table": "...",
 * "op_type": "C/I/U/D", "op_ts": "...", "current_ts": "...", "pos": "...", "primary_keys": [...],
 * "before": {...}, "after": {...} }
 */
public class CustomJsonEnhancer extends AbstractCdcJsonEnhancer
        implements IUpdateSplittableEnhancer {

    @Override
    public JsonNode getBeforeData(JsonNode valueNode) throws CdcJsonEnhanceException {
        JsonNode payload = getPayload(valueNode);
        if (payload == null || !payload.isObject()) {
            return null;
        }
        return payload.get("before");
    }

    @Override
    public JsonNode getAfterData(JsonNode valueNode) throws CdcJsonEnhanceException {
        JsonNode payload = getPayload(valueNode);
        if (payload == null || !payload.isObject()) {
            return null;
        }
        return payload.get("after");
    }

    @Override
    public String getFormatName() {
        return "CUSTOM_JSON";
    }

    @Override
    public int getPriority() {
        return 4;
    }

    @Override
    public boolean canHandle(JsonNode valueNode) {
        if (valueNode == null || !valueNode.isObject()) {
            return false;
        }

        // Must not have "payload" (to distinguish from Debezium)
        if (valueNode.has("payload")) {
            return false;
        }

        // Must have required fields: table, op_type, primary_keys
        JsonNode table = valueNode.get("table");
        JsonNode opType = valueNode.get("op_type");
        JsonNode primaryKeys = valueNode.get("primary_keys");

        if (table == null || !table.isTextual()) {
            return false;
        }
        if (opType == null || !opType.isTextual()) {
            return false;
        }
        if (primaryKeys == null || !primaryKeys.isArray()) {
            return false;
        }

        // op_type must be C/I/U/D (uppercase)
        String opTypeValue = opType.asText();
        return "C".equals(opTypeValue)
                || "I".equals(opTypeValue)
                || "U".equals(opTypeValue)
                || "D".equals(opTypeValue);
    }

    @Override
    public RowKind parseRowKind(JsonNode valueNode) {
        if (!canHandle(valueNode)) {
            return null;
        }

        JsonNode opType = valueNode.get("op_type");
        String opTypeValue = opType.asText();

        switch (opTypeValue) {
            case "C":
            case "I":
                return RowKind.INSERT;
            case "U":
                return RowKind.UPDATE_AFTER;
            case "D":
                return RowKind.DELETE;
            default:
                return null;
        }
    }

    @Override
    public JsonNode enhance(
            JsonNode valueNode,
            RowKind originalRowKind,
            RowKind targetRowKind,
            Map<String, Object> fieldsToAdd)
            throws CdcJsonEnhanceException {
        if (!canHandle(valueNode)) {
            throw new CdcJsonEnhanceException("Cannot enhance non-Custom JSON: " + valueNode);
        }

        ObjectNode root = (ObjectNode) valueNode;

        boolean convertDeleteToAfter =
                originalRowKind == RowKind.DELETE
                        && (targetRowKind == RowKind.UPDATE_AFTER
                                || targetRowKind == RowKind.INSERT);

        // Special handling for DELETE converted to UPDATE_AFTER / INSERT
        if (convertDeleteToAfter) {
            JsonNode before = root.get("before");
            if (before != null && before.isObject()) {
                ObjectNode after = before.deepCopy();
                addFieldsToNode(after, fieldsToAdd);
                root.set("after", after);
            }
        } else if (originalRowKind == RowKind.DELETE) {
            // For DELETE (not converted), add to "before" node
            JsonNode before = root.get("before");
            if (before != null && before.isObject()) {
                addFieldsToNode((ObjectNode) before, fieldsToAdd);
            }
        } else {
            // For INSERT/UPDATE, add to "after" node
            JsonNode after = root.get("after");
            if (after != null && after.isObject()) {
                addFieldsToNode((ObjectNode) after, fieldsToAdd);
            }
        }

        // Synchronize operation type if RowKind changed
        if (originalRowKind != targetRowKind) {
            String newOpType = mapRowKindToCustomOpType(targetRowKind);
            root.put("op_type", newOpType);
        }

        // Keep output self-consistent with me/custom_json.txt
        JsonNode opType = root.get("op_type");
        if (opType != null && opType.isTextual()) {
            String opTypeValue = opType.asText();
            if ("C".equals(opTypeValue) || "I".equals(opTypeValue)) {
                root.remove("before");
            } else if ("D".equals(opTypeValue)) {
                root.remove("after");
            }
        }

        return root;
    }

    /**
     * Map RowKind to Custom JSON operation type
     *
     * @param rowKind The RowKind
     * @return Custom operation type (C/I/U/D)
     */
    private String mapRowKindToCustomOpType(RowKind rowKind) {
        switch (rowKind) {
            case INSERT:
                return "I";
            case UPDATE_AFTER:
                return "U";
            case DELETE:
                return "D";
            default:
                throw new CdcJsonEnhanceException("Unsupported RowKind: " + rowKind);
        }
    }

    /**
     * Get the payload node from Custom JSON
     *
     * <p>Custom format has no "payload" wrapper - before/after are directly at root level So we
     * return the root node itself as the "payload"
     *
     * @param valueNode The Custom JSON value
     * @return The root node itself (acts as payload)
     */
    @Override
    public JsonNode getPayload(JsonNode valueNode) {
        if (valueNode == null || !valueNode.isObject()) {
            return null;
        }
        // For Custom JSON, the root node itself is the "payload"
        return valueNode;
    }

    /**
     * Build a new Custom JSON format node
     *
     * @param before The before data (can be null)
     * @param after The after data
     * @param op The operation type (accepts both Debezium "c/r/u/d" and Custom "C/I/U/D" formats)
     * @return New Custom JSON node
     * @throws CdcJsonEnhanceException if build fails
     */
    @Override
    public JsonNode buildPayload(JsonNode before, JsonNode after, String op)
            throws CdcJsonEnhanceException {
        ObjectNode customNode = JsonNodeFactory.instance.objectNode();

        // Convert Debezium operation format (c/r/u/d) to Custom format (C/I/U/D)
        String customOp;
        switch (op) {
            case "c":
            case "r":
                customOp = "I"; // CREATE/READ -> INSERT
                break;
            case "u":
                customOp = "U"; // UPDATE
                break;
            case "d":
                customOp = "D"; // DELETE
                break;
            default:
                // Assume already in Custom format (C/I/U/D) or pass through as-is
                customOp = op;
        }

        customNode.put("op_type", customOp);

        // Keep output self-consistent with me/custom_json.txt:
        // - C/I: only "after"
        // - D: only "before"
        // - U: both "before" and "after"
        if ("D".equals(customOp)) {
            if (before != null) {
                customNode.set("before", before);
            }
        } else if ("U".equals(customOp)) {
            if (before != null) {
                customNode.set("before", before);
            }
            if (after != null) {
                customNode.set("after", after);
            }
        } else {
            if (after != null) {
                customNode.set("after", after);
            }
        }

        return customNode;
    }

    /**
     * Replace the "payload" in Custom JSON
     *
     * <p>Since Custom JSON has no payload wrapper, this method simply returns the new payload as
     * the new root node
     *
     * @param originalNode The original Custom JSON node (ignored for Custom)
     * @param newPayload The new Custom JSON node to use
     * @return The new payload node as the new root
     * @throws CdcJsonEnhanceException if replacement fails
     */
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
        result.remove("op_type");
        result.remove("before");
        result.remove("after");

        ObjectNode patch = (ObjectNode) newPayload;
        patch.fields().forEachRemaining(entry -> result.set(entry.getKey(), entry.getValue()));
        return result;
    }
}

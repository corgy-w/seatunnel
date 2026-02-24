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
 * OGG JSON format enhancer
 *
 * <p>Handles Oracle GoldenGate (OGG) JSON format with structure: { "before": {...}, "after": {...},
 * "op_type": "I/U/D" }
 */
public class OggJsonEnhancer extends AbstractCdcJsonEnhancer implements IUpdateSplittableEnhancer {

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
        return "OGG_JSON";
    }

    @Override
    public int getPriority() {
        return 2;
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

        // Must have "op_type" field
        JsonNode opType = valueNode.get("op_type");
        if (opType == null || !opType.isTextual()) {
            return false;
        }

        // Should have "before" or "after" field
        return valueNode.has("before") || valueNode.has("after");
    }

    @Override
    public RowKind parseRowKind(JsonNode valueNode) {
        if (!canHandle(valueNode)) {
            return null;
        }

        JsonNode opType = valueNode.get("op_type");
        String opTypeValue = opType.asText();

        switch (opTypeValue) {
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
            throw new CdcJsonEnhanceException("Cannot enhance non-OGG JSON: " + valueNode);
        }

        ObjectNode root = (ObjectNode) valueNode;

        boolean convertDeleteToAfter =
                originalRowKind == RowKind.DELETE
                        && (targetRowKind == RowKind.UPDATE_AFTER
                                || targetRowKind == RowKind.INSERT);

        // Special handling for DELETE converted to UPDATE_AFTER / INSERT
        // (SOFT_DELETE / ADD_DML_MARKER / APPEND_MODE)
        if (convertDeleteToAfter) {
            // For DELETE converted to UPDATE_AFTER, we need to:
            // 1. Keep "before" as-is
            // 2. Create "after" by copying from "before" and adding new fields
            JsonNode before = root.get("before");
            if (before != null && before.isObject()) {
                // Clone the "before" node to create "after"
                ObjectNode after = before.deepCopy();
                // Add new fields to "after"
                addFieldsToNode(after, fieldsToAdd);
                // Set the "after" node in root
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
            String newOpType = mapRowKindToOggOpType(targetRowKind);
            root.put("op_type", newOpType);
        }

        return root;
    }

    /**
     * Map RowKind to OGG operation type
     *
     * @param rowKind The RowKind
     * @return OGG operation type (I/U/D)
     */
    private String mapRowKindToOggOpType(RowKind rowKind) {
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
     * Get the payload node from OGG JSON
     *
     * <p>OGG format has no "payload" wrapper - before/after are directly at root level So we return
     * the root node itself as the "payload"
     *
     * @param valueNode The OGG JSON value
     * @return The root node itself (acts as payload)
     */
    @Override
    public JsonNode getPayload(JsonNode valueNode) {
        if (valueNode == null || !valueNode.isObject()) {
            return null;
        }
        // For OGG, the root node itself is the "payload" (contains before/after)
        return valueNode;
    }

    /**
     * Build a new OGG-format node (root level, not a payload wrapper)
     *
     * @param before The before data (can be null)
     * @param after The after data
     * @param op The operation type (accepts both Debezium "c/r/u/d" and OGG "I/U/D" formats)
     * @return New OGG JSON node
     * @throws CdcJsonEnhanceException if build fails
     */
    @Override
    public JsonNode buildPayload(JsonNode before, JsonNode after, String op)
            throws CdcJsonEnhanceException {
        ObjectNode oggNode = JsonNodeFactory.instance.objectNode();

        // Convert Debezium operation format (c/r/u/d) to OGG format (I/U/D)
        String oggOp;
        switch (op) {
            case "c":
            case "r":
                oggOp = "I"; // CREATE/READ -> INSERT
                break;
            case "u":
                oggOp = "U"; // UPDATE
                break;
            case "d":
                oggOp = "D"; // DELETE
                break;
            default:
                // Assume already in OGG format (I/U/D) or pass through as-is
                oggOp = op;
        }

        oggNode.put("op_type", oggOp);
        if (before != null) {
            oggNode.set("before", before);
        } else {
            oggNode.putNull("before");
        }
        if (after != null) {
            oggNode.set("after", after);
        } else {
            oggNode.putNull("after");
        }
        return oggNode;
    }

    /**
     * Replace the "payload" in OGG JSON
     *
     * <p>Since OGG has no payload wrapper, this method simply returns the new payload as the new
     * root node
     *
     * @param originalNode The original OGG JSON node (ignored for OGG)
     * @param newPayload The new OGG JSON node to use
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

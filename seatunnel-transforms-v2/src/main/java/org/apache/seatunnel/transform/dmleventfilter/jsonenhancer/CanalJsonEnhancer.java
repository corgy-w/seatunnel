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
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.seatunnel.api.table.type.RowKind;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Canal JSON format enhancer
 *
 * <p>Handles Canal JSON format with structure: { "data": [{...}, {...}], "old": [{...}], "type":
 * "INSERT/UPDATE/DELETE" }
 *
 * <p>Note: Canal JSON can contain multiple records in the "data" array. All records must be
 * enhanced.
 */
@Slf4j
public class CanalJsonEnhancer extends AbstractCdcJsonEnhancer
        implements IUpdateSplittableEnhancer {

    @Override
    public String getFormatName() {
        return "CANAL_JSON";
    }

    @Override
    public int getPriority() {
        return 3;
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

        // Must have "data" array and "type" string
        JsonNode data = valueNode.get("data");
        JsonNode type = valueNode.get("type");

        return data != null && data.isArray() && type != null && type.isTextual();
    }

    @Override
    public RowKind parseRowKind(JsonNode valueNode) {
        if (!canHandle(valueNode)) {
            return null;
        }

        JsonNode type = valueNode.get("type");
        String typeValue = type.asText();

        switch (typeValue) {
            case "INSERT":
                return RowKind.INSERT;
            case "UPDATE":
                return RowKind.UPDATE_AFTER;
            case "DELETE":
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
            throw new CdcJsonEnhanceException("Cannot enhance non-Canal JSON: " + valueNode);
        }

        ObjectNode root = (ObjectNode) valueNode;

        // Process all records in data array (CRITICAL: not just data[0])
        JsonNode data = root.get("data");
        if (data.isArray()) {
            ArrayNode dataArray = (ArrayNode) data;
            for (int i = 0; i < dataArray.size(); i++) {
                JsonNode record = dataArray.get(i);
                if (record.isObject()) {
                    addFieldsToNode((ObjectNode) record, fieldsToAdd);
                }
            }
        }

        // For UPDATE with old array, also process old array
        if (originalRowKind == RowKind.UPDATE_AFTER) {
            JsonNode old = root.get("old");
            if (old != null && old.isArray()) {
                ArrayNode oldArray = (ArrayNode) old;
                for (int i = 0; i < oldArray.size(); i++) {
                    JsonNode record = oldArray.get(i);
                    if (record.isObject()) {
                        addFieldsToNode((ObjectNode) record, fieldsToAdd);
                    }
                }
            }
        }

        // Synchronize operation type if RowKind changed
        if (originalRowKind != targetRowKind) {
            String newType = mapRowKindToCanalType(targetRowKind);
            root.put("type", newType);
        }

        return root;
    }

    @Override
    public JsonNode getAfterData(JsonNode valueNode) throws CdcJsonEnhanceException {
        if (valueNode == null || !valueNode.isObject()) {
            return null;
        }
        JsonNode data = valueNode.get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            return null;
        }
        // IMPORTANT: For Canal multi-record messages, this only returns the first record
        // Upper layer (DMLEventFilterTransform) should detect and handle multi-record case
        JsonNode first = data.get(0);
        return (first != null && first.isObject()) ? first : null;
    }

    /**
     * Get the size of data array in Canal JSON
     *
     * @param valueNode The Canal JSON value
     * @return Size of data array, or 0 if not available
     */
    public int getDataArraySize(JsonNode valueNode) {
        if (valueNode == null || !valueNode.isObject()) {
            return 0;
        }
        JsonNode data = valueNode.get("data");
        if (data == null || !data.isArray()) {
            return 0;
        }
        return data.size();
    }

    /**
     * Get a specific record from Canal data array by index
     *
     * @param valueNode The Canal JSON value
     * @param index Index of the record to extract
     * @return The record at specified index, or null if not available
     */
    public JsonNode getDataAtIndex(JsonNode valueNode, int index) {
        if (valueNode == null || !valueNode.isObject()) {
            return null;
        }
        JsonNode data = valueNode.get("data");
        if (data == null || !data.isArray() || index >= data.size()) {
            return null;
        }
        JsonNode record = data.get(index);
        return (record != null && record.isObject()) ? record : null;
    }

    /**
     * Get old data at specific index for UPDATE operations
     *
     * @param valueNode The Canal JSON value
     * @param index Index of the old record
     * @return The before data reconstructed from after + old, or null if not available
     */
    public JsonNode getBeforeDataAtIndex(JsonNode valueNode, int index)
            throws CdcJsonEnhanceException {
        if (valueNode == null || !valueNode.isObject()) {
            return null;
        }
        JsonNode old = valueNode.get("old");
        if (old == null || !old.isArray() || index >= old.size()) {
            return null;
        }
        JsonNode oldRecord = old.get(index);
        if (oldRecord == null || !oldRecord.isObject()) {
            return null;
        }

        // Reconstruct before from after + old
        JsonNode after = getDataAtIndex(valueNode, index);
        ObjectNode before =
                (after != null && after.isObject())
                        ? ((ObjectNode) after).deepCopy()
                        : JsonNodeFactory.instance.objectNode();

        ObjectNode oldObj = (ObjectNode) oldRecord;
        oldObj.fields().forEachRemaining(entry -> before.set(entry.getKey(), entry.getValue()));
        return before;
    }

    @Override
    public JsonNode getBeforeData(JsonNode valueNode) throws CdcJsonEnhanceException {
        if (valueNode == null || !valueNode.isObject()) {
            return null;
        }
        JsonNode old = valueNode.get("old");
        if (old == null || !old.isArray() || old.isEmpty() || !old.get(0).isObject()) {
            return null;
        }

        // IMPORTANT: Reconstruct before data from after + old
        // old array contains only changed fields, need to merge with after to get complete before
        // state
        JsonNode after = getAfterData(valueNode);
        ObjectNode before =
                (after != null && after.isObject())
                        ? ((ObjectNode) after).deepCopy()
                        : JsonNodeFactory.instance.objectNode();

        ObjectNode oldObj = (ObjectNode) old.get(0);
        oldObj.fields().forEachRemaining(entry -> before.set(entry.getKey(), entry.getValue()));
        return before;
    }

    /**
     * Map RowKind to Canal operation type
     *
     * @param rowKind The RowKind
     * @return Canal operation type (INSERT/UPDATE/DELETE)
     */
    private String mapRowKindToCanalType(RowKind rowKind) {
        switch (rowKind) {
            case INSERT:
                return "INSERT";
            case UPDATE_AFTER:
                return "UPDATE";
            case DELETE:
                return "DELETE";
            default:
                throw new CdcJsonEnhanceException("Unsupported RowKind: " + rowKind);
        }
    }

    /**
     * Get the payload node from Canal JSON
     *
     * <p>Canal format has no "payload" wrapper - data array is directly at root level So we return
     * the root node itself as the "payload"
     *
     * @param valueNode The Canal JSON value
     * @return The root node itself (acts as payload)
     */
    @Override
    public JsonNode getPayload(JsonNode valueNode) {
        if (valueNode == null || !valueNode.isObject()) {
            return null;
        }
        // For Canal, the root node itself is the "payload" (contains data array)
        return valueNode;
    }

    /**
     * Build a new Canal-format node (root level, not a payload wrapper)
     *
     * <p>Note: Canal uses array structure (data/old), not before/after. The before/after parameters
     * are mapped to old/data arrays with single element.
     *
     * @param before The before data (mapped to "old" array, can be null)
     * @param after The after data (mapped to "data" array)
     * @param op The operation type (Canal uses "INSERT/UPDATE/DELETE" format)
     * @return New Canal JSON node
     * @throws CdcJsonEnhanceException if build fails
     */
    @Override
    public JsonNode buildPayload(JsonNode before, JsonNode after, String op)
            throws CdcJsonEnhanceException {
        ObjectNode canalNode = JsonNodeFactory.instance.objectNode();

        // Map operation type: "c" -> "INSERT", etc.
        String canalOp;
        switch (op) {
            case "c":
            case "r":
                canalOp = "INSERT";
                break;
            case "u":
                canalOp = "UPDATE";
                break;
            case "d":
                canalOp = "DELETE";
                break;
            default:
                canalOp = op; // Use as-is if already in Canal format
        }
        canalNode.put("type", canalOp);

        // Create data array
        ArrayNode dataArray = JsonNodeFactory.instance.arrayNode();
        if (after != null) {
            dataArray.add(after);
        }
        canalNode.set("data", dataArray);

        // Create old array if before exists
        if (before != null) {
            ArrayNode oldArray = JsonNodeFactory.instance.arrayNode();
            oldArray.add(before);
            canalNode.set("old", oldArray);
        }

        return canalNode;
    }

    /**
     * Replace the "payload" in Canal JSON
     *
     * <p>Since Canal has no payload wrapper, this method simply returns the new payload as the new
     * root node
     *
     * @param originalNode The original Canal JSON node (ignored for Canal)
     * @param newPayload The new Canal JSON node to use
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
        result.remove("type");
        result.remove("data");
        result.remove("old");

        ObjectNode patch = (ObjectNode) newPayload;
        patch.fields().forEachRemaining(entry -> result.set(entry.getKey(), entry.getValue()));
        return result;
    }
}

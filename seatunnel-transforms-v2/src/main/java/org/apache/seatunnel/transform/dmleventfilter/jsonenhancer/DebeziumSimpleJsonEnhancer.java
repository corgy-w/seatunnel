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
import org.apache.seatunnel.shade.com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.seatunnel.api.table.type.RowKind;

import java.util.Map;

/**
 * Debezium simple JSON format enhancer.
 *
 * <p>Format: {"op":"c/r/u/d","before":{...}|null,"after":{...}|null, ...metadata...}
 */
public class DebeziumSimpleJsonEnhancer extends AbstractCdcJsonEnhancer
        implements IUpdateSplittableEnhancer {

    @Override
    public String getFormatName() {
        return "DEBEZIUM_SIMPLE_JSON";
    }

    @Override
    public int getPriority() {
        // Lower priority than standard Debezium / Compatible Debezium.
        return 2;
    }

    @Override
    public boolean canHandle(JsonNode valueNode) {
        if (valueNode == null || !valueNode.isObject()) {
            return false;
        }

        // Must NOT have "payload" field (that's standard Debezium format).
        if (valueNode.has("payload")) {
            return false;
        }

        // Avoid false positives on other CDC formats that also use before/after at root level.
        // - OGG: uses "op_type"
        // - Canal/Kingbase: uses "type"/"data"
        if (valueNode.has("op_type") || valueNode.has("type")) {
            return false;
        }

        if (!valueNode.has("before") && !valueNode.has("after")) {
            return false;
        }

        // Optional: check if "op" field is present and valid.
        if (valueNode.has("op")) {
            JsonNode op = valueNode.get("op");
            if (!op.isTextual()) {
                return false;
            }
            String opValue = op.asText();
            return "c".equals(opValue)
                    || "r".equals(opValue)
                    || "u".equals(opValue)
                    || "d".equals(opValue);
        }

        return true;
    }

    @Override
    public RowKind parseRowKind(JsonNode valueNode) {
        if (!canHandle(valueNode)) {
            return null;
        }

        if (valueNode.has("op") && valueNode.get("op").isTextual()) {
            switch (valueNode.get("op").asText()) {
                case "c":
                case "r":
                    return RowKind.INSERT;
                case "u":
                    return RowKind.UPDATE_AFTER;
                case "d":
                    return RowKind.DELETE;
                default:
                    return null;
            }
        }

        boolean hasBefore = valueNode.has("before") && !valueNode.get("before").isNull();
        boolean hasAfter = valueNode.has("after") && !valueNode.get("after").isNull();

        if (!hasBefore && hasAfter) {
            return RowKind.INSERT;
        }
        if (hasBefore && hasAfter) {
            return RowKind.UPDATE_AFTER;
        }
        if (hasBefore) {
            return RowKind.DELETE;
        }
        return null;
    }

    @Override
    public JsonNode getPayload(JsonNode valueNode) {
        // Simple Debezium has no wrapper; the root node itself acts as the payload.
        return (valueNode != null && valueNode.isObject()) ? valueNode : null;
    }

    @Override
    public JsonNode enhance(
            JsonNode valueNode,
            RowKind originalRowKind,
            RowKind targetRowKind,
            Map<String, Object> fieldsToAdd)
            throws CdcJsonEnhanceException {
        if (!canHandle(valueNode)) {
            throw new CdcJsonEnhanceException(
                    "Cannot enhance non-Debezium-simple JSON: " + valueNode);
        }

        ObjectNode root = (ObjectNode) valueNode;

        boolean convertDeleteToAfter =
                originalRowKind == RowKind.DELETE
                        && (targetRowKind == RowKind.UPDATE_AFTER
                                || targetRowKind == RowKind.INSERT);

        // Special handling for DELETE converted to UPDATE_AFTER / INSERT
        // (SOFT_DELETE / ADD_DML_MARKER / APPEND_MODE)
        if (convertDeleteToAfter) {
            JsonNode before = root.get("before");
            if (before != null && before.isObject()) {
                ObjectNode after = before.deepCopy();
                addFieldsToNode(after, fieldsToAdd);
                root.set("after", after);
            }
            if (targetRowKind == RowKind.INSERT) {
                root.putNull("before");
            }
        } else if (originalRowKind == RowKind.DELETE) {
            JsonNode before = root.get("before");
            if (before != null && before.isObject()) {
                addFieldsToNode((ObjectNode) before, fieldsToAdd);
            }
        } else {
            JsonNode after = root.get("after");
            if (after != null && after.isObject()) {
                addFieldsToNode((ObjectNode) after, fieldsToAdd);
            }
        }

        // Synchronize operation type if RowKind changed.
        if (originalRowKind != targetRowKind) {
            root.put("op", mapRowKindToDebeziumOp(targetRowKind));
        }

        return root;
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

        // Preserve all existing top-level metadata fields and only replace op/before/after.
        ObjectNode result = ((ObjectNode) originalNode).deepCopy();
        ObjectNode patch = (ObjectNode) newPayload;
        result.set("op", patch.get("op"));
        result.set("before", patch.get("before"));
        result.set("after", patch.get("after"));
        return result;
    }

    private String mapRowKindToDebeziumOp(RowKind rowKind) {
        switch (rowKind) {
            case INSERT:
                return "c";
            case UPDATE_AFTER:
                return "u";
            case DELETE:
                return "d";
            default:
                throw new CdcJsonEnhanceException("Unsupported RowKind: " + rowKind);
        }
    }
}

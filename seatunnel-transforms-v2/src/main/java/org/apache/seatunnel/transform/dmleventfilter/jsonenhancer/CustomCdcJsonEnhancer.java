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

import java.util.Map;

/**
 * Custom CDC JSON enhancer driven by {@link CustomCdcConfig}. It supports either array-based or
 * object-based payloads depending on the configuration.
 */
public class CustomCdcJsonEnhancer extends AbstractCdcJsonEnhancer
        implements IUpdateSplittableEnhancer {

    private final CustomCdcConfig config;

    public CustomCdcJsonEnhancer(CustomCdcConfig config) {
        this.config = config;
    }

    @Override
    public String getFormatName() {
        return "CUSTOM_CDC_JSON";
    }

    @Override
    public int getPriority() {
        // Custom CDC JSON should take precedence over built-in enhancers when configured.
        return 0;
    }

    @Override
    public boolean canHandle(JsonNode valueNode) {
        if (valueNode == null || !valueNode.isObject() || config == null) {
            return false;
        }
        if (!valueNode.has(config.getOperationTypeField())) {
            return false;
        }
        // Check if it has required data fields
        if (config.isDualFieldMode()) {
            // In dual-field mode, at least one of before/after should exist
            return valueNode.has(config.getBeforeField()) || valueNode.has(config.getAfterField());
        } else {
            // In single-field mode, dataField must exist
            return valueNode.has(config.getDataField());
        }
    }

    @Override
    public RowKind parseRowKind(JsonNode valueNode) {
        if (!canHandle(valueNode)) {
            return null;
        }
        JsonNode opNode = valueNode.get(config.getOperationTypeField());
        if (opNode == null || !opNode.isTextual()) {
            return null;
        }
        return config.getOperationTypeMapping().get(opNode.asText());
    }

    @Override
    public JsonNode enhance(
            JsonNode valueNode,
            RowKind originalRowKind,
            RowKind targetRowKind,
            Map<String, Object> fieldsToAdd)
            throws CdcJsonEnhanceException {
        if (!canHandle(valueNode)) {
            throw new CdcJsonEnhanceException("Cannot enhance non-custom CDC JSON: " + valueNode);
        }

        ObjectNode root = (ObjectNode) valueNode;

        if (config.isDualFieldMode()) {
            // Dual-field mode (before/after like Debezium)
            enhanceDualFieldMode(root, originalRowKind, targetRowKind, fieldsToAdd);
        } else {
            // Single-field mode (legacy)
            enhanceSingleFieldMode(root, fieldsToAdd);
        }

        // Update operation type field
        Map<RowKind, String> reverse = config.getReverseMapping();
        if (reverse != null && reverse.containsKey(targetRowKind)) {
            root.put(config.getOperationTypeField(), reverse.get(targetRowKind));
        }

        return root;
    }

    @Override
    public JsonNode getBeforeData(JsonNode valueNode) throws CdcJsonEnhanceException {
        if (config.isDualFieldMode()) {
            return valueNode.get(config.getBeforeField());
        }
        return null;
    }

    @Override
    public JsonNode getAfterData(JsonNode valueNode) throws CdcJsonEnhanceException {
        if (config.isDualFieldMode()) {
            return valueNode.get(config.getAfterField());
        }
        return valueNode.get(config.getDataField());
    }

    @Override
    public JsonNode getPayload(JsonNode valueNode) {
        return valueNode;
    }

    @Override
    public JsonNode buildPayload(JsonNode before, JsonNode after, String op) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();

        // Map op ("c", "u", "d") to custom op string using reverse mapping
        RowKind kind = RowKind.INSERT;
        if ("c".equals(op)) kind = RowKind.INSERT;
        else if ("u".equals(op)) kind = RowKind.UPDATE_AFTER;
        else if ("d".equals(op)) kind = RowKind.DELETE;

        String customOp = config.getReverseMapping().get(kind);
        if (customOp == null) {
            customOp = op;
        }
        root.put(config.getOperationTypeField(), customOp);

        if (config.isDualFieldMode()) {
            if (before != null) {
                root.set(config.getBeforeField(), before);
            }
            if (after != null) {
                root.set(config.getAfterField(), after);
            }
        } else {
            if (after != null) {
                root.set(config.getDataField(), after);
            } else if (before != null) {
                root.set(config.getDataField(), before);
            }
        }
        return root;
    }

    @Override
    public JsonNode replacePayload(JsonNode originalNode, JsonNode newPayload) {
        ObjectNode result = ((ObjectNode) originalNode).deepCopy();

        String opField = config.getOperationTypeField();
        if (newPayload.has(opField)) {
            result.set(opField, newPayload.get(opField));
        }

        if (config.isDualFieldMode()) {
            String beforeField = config.getBeforeField();
            String afterField = config.getAfterField();

            if (newPayload.has(beforeField)) {
                result.set(beforeField, newPayload.get(beforeField));
            } else {
                result.remove(beforeField);
            }

            if (newPayload.has(afterField)) {
                result.set(afterField, newPayload.get(afterField));
            } else {
                result.remove(afterField);
            }
        } else {
            String dataField = config.getDataField();
            if (newPayload.has(dataField)) {
                result.set(dataField, newPayload.get(dataField));
            } else {
                result.remove(dataField);
            }
        }
        return result;
    }

    /**
     * Enhance in dual-field mode (before/after).
     *
     * <p>This mode is similar to Debezium/OGG:
     *
     * <ul>
     *   <li>DELETE: has before only
     *   <li>INSERT: has after only
     *   <li>UPDATE: has both before and after
     * </ul>
     */
    private void enhanceDualFieldMode(
            ObjectNode root,
            RowKind originalRowKind,
            RowKind targetRowKind,
            Map<String, Object> fieldsToAdd)
            throws CdcJsonEnhanceException {

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
            JsonNode before = root.get(config.getBeforeField());
            if (before != null && before.isObject()) {
                // Clone the "before" node to create "after"
                ObjectNode after = before.deepCopy();
                // Add new fields to "after"
                addFieldsToNode(after, fieldsToAdd);
                // Set the "after" node
                root.set(config.getAfterField(), after);
            }
        } else if (originalRowKind == RowKind.DELETE) {
            // For DELETE (not converted), add to "before"
            JsonNode before = root.get(config.getBeforeField());
            if (before != null && before.isObject()) {
                addFieldsToNode((ObjectNode) before, fieldsToAdd);
            }
        } else {
            // For INSERT/UPDATE, add to "after"
            JsonNode after = root.get(config.getAfterField());
            if (after != null && after.isObject()) {
                addFieldsToNode((ObjectNode) after, fieldsToAdd);
            }
        }
    }

    /**
     * Enhance in single-field mode (legacy).
     *
     * <p>This mode uses a single data field for all operations.
     */
    private void enhanceSingleFieldMode(ObjectNode root, Map<String, Object> fieldsToAdd)
            throws CdcJsonEnhanceException {
        JsonNode dataNode = root.get(config.getDataField());
        if (dataNode == null) {
            throw new CdcJsonEnhanceException(
                    "Custom CDC JSON data field missing: " + config.getDataField());
        }

        if (dataNode.isArray()) {
            ArrayNode arrayNode = (ArrayNode) dataNode;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode record = arrayNode.get(i);
                if (record.isObject()) {
                    addFieldsToNode((ObjectNode) record, fieldsToAdd);
                }
            }
        } else if (dataNode.isObject()) {
            addFieldsToNode((ObjectNode) dataNode, fieldsToAdd);
        } else {
            throw new CdcJsonEnhanceException(
                    "Custom CDC data field must be object or array but was: " + dataNode);
        }
    }
}

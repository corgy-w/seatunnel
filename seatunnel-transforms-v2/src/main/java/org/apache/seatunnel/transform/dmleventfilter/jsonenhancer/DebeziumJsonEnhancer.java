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
 * Debezium JSON format enhancer
 *
 * <p>Handles standard Debezium JSON format with structure: { "payload": { "op": "c/r/u/d",
 * "before": {...}, "after": {...} } }
 */
public class DebeziumJsonEnhancer extends AbstractCdcJsonEnhancer
        implements IUpdateSplittableEnhancer {

    @Override
    public String getFormatName() {
        return "DEBEZIUM_JSON";
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public boolean canHandle(JsonNode valueNode) {
        if (valueNode == null || !valueNode.isObject()) {
            return false;
        }
        JsonNode payload = valueNode.get("payload");
        if (payload == null || !payload.isObject()) {
            return false;
        }
        JsonNode op = payload.get("op");
        return op != null && op.isTextual();
    }

    @Override
    public RowKind parseRowKind(JsonNode valueNode) {
        if (!canHandle(valueNode)) {
            return null;
        }

        JsonNode payload = valueNode.get("payload");
        JsonNode op = payload.get("op");
        String opValue = op.asText();

        switch (opValue) {
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

    @Override
    public JsonNode enhance(
            JsonNode valueNode,
            RowKind originalRowKind,
            RowKind targetRowKind,
            Map<String, Object> fieldsToAdd)
            throws CdcJsonEnhanceException {
        if (!canHandle(valueNode)) {
            throw new CdcJsonEnhanceException("Cannot enhance non-Debezium JSON: " + valueNode);
        }

        ObjectNode root = (ObjectNode) valueNode;
        ObjectNode payload = (ObjectNode) root.get("payload");

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
            JsonNode before = payload.get("before");
            if (before != null && before.isObject()) {
                // Clone the "before" node to create "after"
                ObjectNode after = before.deepCopy();
                // Add new fields to "after"
                addFieldsToNode(after, fieldsToAdd);
                // Set the "after" node in payload
                payload.set("after", after);
            }
            // If DELETE is converted to INSERT (APPEND_MODE), align with Debezium INSERT semantics:
            // `before` should be null and the copied row should live in `after`.
            if (targetRowKind == RowKind.INSERT) {
                payload.putNull("before");
            }
        } else if (originalRowKind == RowKind.DELETE) {
            // For DELETE (not converted), add to "before" node
            JsonNode before = payload.get("before");
            if (before != null && before.isObject()) {
                addFieldsToNode((ObjectNode) before, fieldsToAdd);
            }
        } else {
            // For INSERT/UPDATE, add to "after" node
            JsonNode after = payload.get("after");
            if (after != null && after.isObject()) {
                addFieldsToNode((ObjectNode) after, fieldsToAdd);
            }
        }

        // Synchronize operation type if RowKind changed
        if (originalRowKind != targetRowKind) {
            String newOp = mapRowKindToDebeziumOp(targetRowKind);
            payload.put("op", newOp);
        }

        // Synchronize schema if present (Kafka Connect format)
        synchronizeSchemaWithPayload(root, fieldsToAdd, convertDeleteToAfter, targetRowKind);

        return root;
    }

    @Override
    public JsonNode buildPayload(JsonNode before, JsonNode after, String op) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("op", op);
        if (before != null) {
            payload.set("before", before);
        } else {
            payload.putNull("before");
        }
        if (after != null) {
            payload.set("after", after);
        } else {
            payload.putNull("after");
        }
        return payload;
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
        JsonNode originalPayload = result.get("payload");
        ObjectNode mergedPayload =
                originalPayload != null && originalPayload.isObject()
                        ? ((ObjectNode) originalPayload).deepCopy()
                        : JsonNodeFactory.instance.objectNode();

        ObjectNode patch = (ObjectNode) newPayload;
        mergedPayload.set("op", patch.get("op"));
        mergedPayload.set("before", patch.get("before"));
        mergedPayload.set("after", patch.get("after"));

        result.set("payload", mergedPayload);

        // Synchronize schema with the new payload
        synchronizeSchemaFromPayload(result, mergedPayload);

        return result;
    }

    /**
     * Map RowKind to Debezium operation type
     *
     * @param rowKind The RowKind
     * @return Debezium operation type (c/u/d)
     */
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

    /**
     * Synchronize Kafka Connect schema with payload changes when new fields are added.
     *
     * <p>This method ensures schema-payload consistency in Kafka Connect JSON format by updating
     * schema.fields definitions when new fields are added to payload.before/after.
     *
     * @param root The root JSON node containing both schema and payload
     * @param fieldsToAdd Map of new fields added to payload
     * @param convertDeleteToAfter Whether DELETE is being converted to INSERT (affects which schema
     *     to update)
     * @param targetRowKind The target RowKind after conversion
     */
    private void synchronizeSchemaWithPayload(
            ObjectNode root,
            Map<String, Object> fieldsToAdd,
            boolean convertDeleteToAfter,
            RowKind targetRowKind) {
        if (fieldsToAdd == null || fieldsToAdd.isEmpty()) {
            return;
        }

        JsonNode schema = root.get("schema");
        if (schema == null || !schema.isObject()) {
            // No schema present (non-Kafka Connect format), skip synchronization
            return;
        }

        ObjectNode schemaNode = (ObjectNode) schema;
        JsonNode fields = schemaNode.get("fields");
        if (fields == null || !fields.isArray()) {
            return;
        }

        ArrayNode fieldsArray = (ArrayNode) fields;

        // Find "before" and "after" field definitions in schema.fields
        ObjectNode beforeFieldDef = null;
        ObjectNode afterFieldDef = null;

        for (JsonNode fieldDef : fieldsArray) {
            if (fieldDef.isObject()) {
                JsonNode fieldName = fieldDef.get("field");
                if (fieldName != null && fieldName.isTextual()) {
                    if ("before".equals(fieldName.asText())) {
                        beforeFieldDef = (ObjectNode) fieldDef;
                    } else if ("after".equals(fieldName.asText())) {
                        afterFieldDef = (ObjectNode) fieldDef;
                    }
                }
            }
        }

        // Add new field definitions to the appropriate schema struct
        if (convertDeleteToAfter && targetRowKind == RowKind.INSERT) {
            // DELETE converted to INSERT: only update "after" schema
            if (afterFieldDef != null) {
                addFieldDefinitionsToSchema(afterFieldDef, fieldsToAdd);
            }
        } else if (convertDeleteToAfter && targetRowKind == RowKind.UPDATE_AFTER) {
            // DELETE converted to UPDATE: update both "before" (unchanged) and "after" (with new
            // fields)
            if (afterFieldDef != null) {
                addFieldDefinitionsToSchema(afterFieldDef, fieldsToAdd);
            }
        } else if (targetRowKind == RowKind.DELETE) {
            // Normal DELETE: update "before" schema
            if (beforeFieldDef != null) {
                addFieldDefinitionsToSchema(beforeFieldDef, fieldsToAdd);
            }
        } else {
            // INSERT/UPDATE: update "after" schema
            if (afterFieldDef != null) {
                addFieldDefinitionsToSchema(afterFieldDef, fieldsToAdd);
            }
        }
    }

    /**
     * Add field definitions to a schema struct's fields array.
     *
     * @param structFieldDef The schema field definition for "before" or "after" struct
     * @param fieldsToAdd Map of new fields to add
     */
    private void addFieldDefinitionsToSchema(
            ObjectNode structFieldDef, Map<String, Object> fieldsToAdd) {
        JsonNode innerFields = structFieldDef.get("fields");
        if (innerFields == null || !innerFields.isArray()) {
            return;
        }

        ArrayNode innerFieldsArray = (ArrayNode) innerFields;

        // Check which fields already exist to avoid duplicates
        for (Map.Entry<String, Object> entry : fieldsToAdd.entrySet()) {
            String fieldName = entry.getKey();
            Object fieldValue = entry.getValue();

            // Check if field already exists in schema
            boolean fieldExists = false;
            for (JsonNode existingField : innerFieldsArray) {
                if (existingField.isObject()) {
                    JsonNode existingFieldName = existingField.get("field");
                    if (existingFieldName != null
                            && existingFieldName.isTextual()
                            && fieldName.equals(existingFieldName.asText())) {
                        fieldExists = true;
                        break;
                    }
                }
            }

            if (!fieldExists) {
                // Add new field definition
                ObjectNode newFieldDef = JsonNodeFactory.instance.objectNode();
                newFieldDef.put("type", inferSchemaType(fieldValue));
                newFieldDef.put("optional", true);
                newFieldDef.put("field", fieldName);
                innerFieldsArray.add(newFieldDef);
            }
        }
    }

    /**
     * Infer Kafka Connect schema type from field value.
     *
     * @param value The field value
     * @return Schema type string ("string", "int32", "int64", etc.)
     */
    private String inferSchemaType(Object value) {
        if (value == null) {
            return "string";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Integer) {
            return "int32";
        }
        if (value instanceof Long) {
            return "int64";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Double || value instanceof Float) {
            return "double";
        }
        // Default to string for unknown types
        return "string";
    }

    /**
     * Synchronize schema with payload after replacing payload in replacePayload() method.
     *
     * <p>This method extracts field information from the payload and ensures the schema has
     * matching field definitions.
     *
     * @param root The root JSON node containing both schema and payload
     * @param payload The new payload that was set
     */
    private void synchronizeSchemaFromPayload(ObjectNode root, ObjectNode payload) {
        JsonNode schema = root.get("schema");
        if (schema == null || !schema.isObject()) {
            // No schema present, skip synchronization
            return;
        }

        ObjectNode schemaNode = (ObjectNode) schema;
        JsonNode fields = schemaNode.get("fields");
        if (fields == null || !fields.isArray()) {
            return;
        }

        ArrayNode fieldsArray = (ArrayNode) fields;

        // Find "before" and "after" field definitions in schema.fields
        ObjectNode beforeFieldDef = null;
        ObjectNode afterFieldDef = null;

        for (JsonNode fieldDef : fieldsArray) {
            if (fieldDef.isObject()) {
                JsonNode fieldName = fieldDef.get("field");
                if (fieldName != null && fieldName.isTextual()) {
                    if ("before".equals(fieldName.asText())) {
                        beforeFieldDef = (ObjectNode) fieldDef;
                    } else if ("after".equals(fieldName.asText())) {
                        afterFieldDef = (ObjectNode) fieldDef;
                    }
                }
            }
        }

        // Synchronize schema for "before" if present in payload
        JsonNode payloadBefore = payload.get("before");
        if (payloadBefore != null
                && payloadBefore.isObject()
                && beforeFieldDef != null
                && !payloadBefore.isNull()) {
            synchronizeSchemaFieldsWithData(beforeFieldDef, (ObjectNode) payloadBefore);
        }

        // Synchronize schema for "after" if present in payload
        JsonNode payloadAfter = payload.get("after");
        if (payloadAfter != null
                && payloadAfter.isObject()
                && afterFieldDef != null
                && !payloadAfter.isNull()) {
            synchronizeSchemaFieldsWithData(afterFieldDef, (ObjectNode) payloadAfter);
        }
    }

    /**
     * Synchronize schema fields with data fields.
     *
     * @param structFieldDef The schema field definition for "before" or "after" struct
     * @param dataNode The data node containing actual field values
     */
    private void synchronizeSchemaFieldsWithData(ObjectNode structFieldDef, ObjectNode dataNode) {
        JsonNode innerFields = structFieldDef.get("fields");
        if (innerFields == null || !innerFields.isArray()) {
            return;
        }

        ArrayNode innerFieldsArray = (ArrayNode) innerFields;

        // Iterate through all fields in the data
        dataNode.fieldNames()
                .forEachRemaining(
                        fieldName -> {
                            // Check if field already exists in schema
                            boolean fieldExists = false;
                            for (JsonNode existingField : innerFieldsArray) {
                                if (existingField.isObject()) {
                                    JsonNode existingFieldName = existingField.get("field");
                                    if (existingFieldName != null
                                            && existingFieldName.isTextual()
                                            && fieldName.equals(existingFieldName.asText())) {
                                        fieldExists = true;
                                        break;
                                    }
                                }
                            }

                            if (!fieldExists) {
                                // Add new field definition
                                JsonNode fieldValue = dataNode.get(fieldName);
                                ObjectNode newFieldDef = JsonNodeFactory.instance.objectNode();
                                newFieldDef.put("type", inferSchemaTypeFromJsonNode(fieldValue));
                                newFieldDef.put("optional", true);
                                newFieldDef.put("field", fieldName);
                                innerFieldsArray.add(newFieldDef);
                            }
                        });
    }

    /**
     * Infer Kafka Connect schema type from JsonNode.
     *
     * @param node The JsonNode value
     * @return Schema type string ("string", "int32", "int64", etc.)
     */
    private String inferSchemaTypeFromJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return "string";
        }
        if (node.isTextual()) {
            return "string";
        }
        if (node.isInt()) {
            return "int32";
        }
        if (node.isLong()) {
            return "int64";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        if (node.isFloatingPointNumber()) {
            return "double";
        }

        // Default to string for complex types (arrays, objects, etc.)
        return "string";
    }
}

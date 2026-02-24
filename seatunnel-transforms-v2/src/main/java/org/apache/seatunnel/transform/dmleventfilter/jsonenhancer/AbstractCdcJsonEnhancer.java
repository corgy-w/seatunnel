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

import java.util.Map;

/**
 * Abstract base class for CDC JSON enhancers
 *
 * <p>Provides common utility methods for subclasses to add fields to JSON nodes.
 */
public abstract class AbstractCdcJsonEnhancer implements ICdcJsonEnhancer {

    /**
     * Add fields to a JSON object node for example: fieldsToAdd.put("dml_timestamp", "2024-01-01
     * 12:00:00"); // add timestamp fieldsToAdd.put("is_deleted", 0); // add deletion flag
     * fieldsToAdd.put("operation_type", "INSERT"); // add operation type
     *
     * @param node The target object node
     * @param fieldsToAdd Fields to add (name -> value)
     */
    protected void addFieldsToNode(ObjectNode node, Map<String, Object> fieldsToAdd) {
        if (fieldsToAdd == null || fieldsToAdd.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : fieldsToAdd.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                node.putNull(fieldName);
            } else if (value instanceof String) {
                node.put(fieldName, (String) value);
            } else if (value instanceof Integer) {
                node.put(fieldName, (Integer) value);
            } else if (value instanceof Long) {
                node.put(fieldName, (Long) value);
            } else if (value instanceof Boolean) {
                node.put(fieldName, (Boolean) value);
            } else if (value instanceof Double) {
                node.put(fieldName, (Double) value);
            } else if (value instanceof Float) {
                node.put(fieldName, (Float) value);
            } else if (value instanceof JsonNode) {
                node.set(fieldName, (JsonNode) value);
            } else {
                node.put(fieldName, value.toString());
            }
        }
    }

    /**
     * Get the payload node from CDC JSON (default implementation for Debezium format)
     *
     * @param valueNode The CDC JSON value
     * @return Payload node or null if not found
     */
    public JsonNode getPayload(JsonNode valueNode) {
        if (valueNode == null || !valueNode.isObject()) {
            return null;
        }
        return valueNode.get("payload");
    }

    /**
     * Add fields to a data node
     *
     * @param dataNode The data node to enhance
     * @param fieldsToAdd Fields to add (name -> value)
     * @return Enhanced data node
     * @throws CdcJsonEnhanceException if enhancement fails
     */
    public JsonNode addFieldsToData(JsonNode dataNode, Map<String, Object> fieldsToAdd)
            throws CdcJsonEnhanceException {
        if (dataNode == null) {
            throw new CdcJsonEnhanceException("Data node is null");
        }
        if (!dataNode.isObject()) {
            throw new CdcJsonEnhanceException("Data node is not an object: " + dataNode);
        }
        ObjectNode objectNode = (ObjectNode) dataNode.deepCopy();
        addFieldsToNode(objectNode, fieldsToAdd);
        return objectNode;
    }

    /**
     * Build a new payload node (default implementation for Debezium format)
     *
     * @param before The before data (can be null)
     * @param after The after data
     * @param op The operation type
     * @return New payload node
     * @throws CdcJsonEnhanceException if build fails
     */
    public JsonNode buildPayload(JsonNode before, JsonNode after, String op)
            throws CdcJsonEnhanceException {
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

    /**
     * Replace the payload in the original CDC JSON node (default implementation for Debezium
     * format)
     *
     * @param originalNode The original CDC JSON node
     * @param newPayload The new payload to replace with
     * @return New CDC JSON node with replaced payload
     * @throws CdcJsonEnhanceException if replacement fails
     */
    public JsonNode replacePayload(JsonNode originalNode, JsonNode newPayload)
            throws CdcJsonEnhanceException {
        if (originalNode == null || !originalNode.isObject()) {
            throw new CdcJsonEnhanceException(
                    "Original node is null or not an object: " + originalNode);
        }
        if (newPayload == null) {
            throw new CdcJsonEnhanceException("New payload is null");
        }
        ObjectNode result = (ObjectNode) originalNode.deepCopy();
        result.set("payload", newPayload);
        return result;
    }
}

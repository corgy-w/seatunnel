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

import java.util.Map;

/**
 * Extended enhancer interface for CDC formats that support UPDATE event splitting.
 *
 * <p>The split-update flow needs two logical row images (BEFORE / AFTER) and the ability to
 * rebuild/replace the format-specific payload while preserving original metadata.
 *
 * <p>Implementations should:
 *
 * <ul>
 *   <li>Provide BEFORE/AFTER extraction (some formats require reconstruction, e.g. Canal old/data)
 *   <li>Build an INSERT-equivalent payload for split output (op/c/type/op_type mapping)
 *   <li>Replace payload into the original CDC JSON while keeping required top-level fields
 * </ul>
 */
public interface IUpdateSplittableEnhancer extends ICdcJsonEnhancer {

    /**
     * Extract the BEFORE row image from the CDC JSON.
     *
     * <p>Return null if BEFORE is not available.
     */
    default JsonNode getBeforeData(JsonNode valueNode) throws CdcJsonEnhanceException {
        JsonNode payload = getPayload(valueNode);
        if (payload == null || !payload.isObject()) {
            return null;
        }
        return payload.get("before");
    }

    /**
     * Extract the AFTER row image from the CDC JSON.
     *
     * <p>Return null if AFTER is not available.
     */
    default JsonNode getAfterData(JsonNode valueNode) throws CdcJsonEnhanceException {
        JsonNode payload = getPayload(valueNode);
        if (payload == null || !payload.isObject()) {
            return null;
        }
        return payload.get("after");
    }

    /**
     * Get the payload node from CDC JSON.
     *
     * <p>For Debezium-like formats, payload is nested (value.payload). For formats without a
     * wrapper (OGG/Custom/Canal/Kingbase), payload is typically the root object.
     */
    JsonNode getPayload(JsonNode valueNode);

    /**
     * Add fields to a row image node (before/after).
     *
     * <p>Implementations should deep-copy and return a new node.
     */
    JsonNode addFieldsToData(JsonNode dataNode, Map<String, Object> fieldsToAdd)
            throws CdcJsonEnhanceException;

    /**
     * Build a new format-specific payload node for output.
     *
     * <p>Callers typically pass Debezium-style op ("c/r/u/d"); implementations should map it to the
     * format-specific operation field.
     */
    JsonNode buildPayload(JsonNode before, JsonNode after, String op)
            throws CdcJsonEnhanceException;

    /**
     * Replace the payload in the original CDC JSON node.
     *
     * <p>Implementations must preserve required metadata fields from {@code originalNode}.
     */
    JsonNode replacePayload(JsonNode originalNode, JsonNode newPayload)
            throws CdcJsonEnhanceException;
}

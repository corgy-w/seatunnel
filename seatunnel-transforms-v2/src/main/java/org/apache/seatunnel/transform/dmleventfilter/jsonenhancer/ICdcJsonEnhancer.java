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

import org.apache.seatunnel.api.table.type.RowKind;

import java.io.Serializable;
import java.util.Map;

/**
 * CDC JSON Enhancer interface
 *
 * <p>Responsibilities: 1. Format detection 2. RowKind parsing (parse operation type from CDC JSON)
 * 3. JSON enhancement (add fields inside value JSON)
 *
 * <p>⚠️ IMPORTANT: When implementing a new CDC JSON format enhancer:
 *
 * <ol>
 *   <li>Implement this interface (or extend AbstractCdcJsonEnhancer)
 *   <li>Add to CdcJsonEnhancerManager.initializeEnhancers() method
 *   <li>Add to DMLEventFilterTransform.TableProcessor.createEnhancerByFormat() method
 *   <li>Optionally add to Kafka MessageFormat enum if used by Kafka source
 * </ol>
 */
public interface ICdcJsonEnhancer extends Serializable {

    /**
     * Get format name for logging
     *
     * @return format name
     */
    String getFormatName();

    /**
     * Get detection priority (lower number = higher priority)
     *
     * @return priority number
     */
    int getPriority();

    /**
     * Check if this enhancer can handle the given value JSON
     *
     * @param valueNode The CDC JSON value
     * @return true if this enhancer can handle it
     */
    boolean canHandle(JsonNode valueNode);

    /**
     * Parse RowKind from CDC JSON
     *
     * @param valueNode The CDC JSON value
     * @return RowKind or null if cannot parse
     */
    RowKind parseRowKind(JsonNode valueNode);

    /**
     * Enhance the value JSON
     *
     * @param valueNode The CDC JSON value
     * @param originalRowKind The original RowKind (parsed from CDC JSON)
     * @param targetRowKind The target RowKind after transformation
     * @param fieldsToAdd Fields to add (name -> value)
     * @return Enhanced JSON
     * @throws CdcJsonEnhanceException if enhancement fails
     */
    JsonNode enhance(
            JsonNode valueNode,
            RowKind originalRowKind,
            RowKind targetRowKind,
            Map<String, Object> fieldsToAdd)
            throws CdcJsonEnhanceException;
}

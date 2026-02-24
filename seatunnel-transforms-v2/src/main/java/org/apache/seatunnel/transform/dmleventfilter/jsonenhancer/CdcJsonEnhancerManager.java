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

import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CDC JSON Enhancer Manager
 *
 * <p>Manages all CDC JSON enhancers and provides format detection and enhancement capabilities.
 */
@Slf4j
public class CdcJsonEnhancerManager implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<ICdcJsonEnhancer> enhancers;
    private final Map<CustomCdcConfig, CustomCdcJsonEnhancer> customEnhancers;

    private CdcJsonEnhancerManager(boolean registerDefaults) {
        this.enhancers = new ArrayList<>();
        this.customEnhancers = new ConcurrentHashMap<>();
        if (registerDefaults) {
            initializeEnhancers();
        }
    }

    /**
     * Create a new manager instance.
     *
     * <p>Each Transform instance should use its own manager to ensure proper isolation in
     * distributed environments.
     */
    public static CdcJsonEnhancerManager create() {
        return new CdcJsonEnhancerManager(true);
    }

    /** Create a new manager instance (for testing purposes). */
    public static CdcJsonEnhancerManager createForTest() {
        return new CdcJsonEnhancerManager(true);
    }

    /**
     * Initialize all supported enhancers
     *
     * <p>⚠️ IMPORTANT: When adding a new CDC JSON format enhancer:
     *
     * <ol>
     *   <li>Add the new enhancer here: enhancers.add(new XxxJsonEnhancer())
     *   <li>Update DMLEventFilterTransform.TableProcessor.createFormatEnhancerMap() method
     *   <li>Optionally add to Kafka MessageFormat enum if used by Kafka source
     * </ol>
     */
    private void initializeEnhancers() {
        // Register fully implemented enhancers
        enhancers.add(new DebeziumJsonEnhancer());
        enhancers.add(new CompatibleDebeziumJsonEnhancer());
        enhancers.add(new OggJsonEnhancer());
        enhancers.add(new CanalJsonEnhancer());
        enhancers.add(new KingbaseJsonEnhancer());
        enhancers.add(new CustomJsonEnhancer());

        // Sort by priority (lower number = higher priority)
        enhancers.sort(Comparator.comparingInt(ICdcJsonEnhancer::getPriority));

        log.info("Initialized {} CDC JSON enhancers", enhancers.size());
        for (ICdcJsonEnhancer enhancer : enhancers) {
            log.info("  - {} (priority: {})", enhancer.getFormatName(), enhancer.getPriority());
        }
    }

    /**
     * Detect which enhancer can handle the given value JSON
     *
     * @param valueNode The CDC JSON value
     * @return The first enhancer that can handle it, or null if none found
     */
    public ICdcJsonEnhancer detectEnhancer(JsonNode valueNode) {
        if (valueNode == null) {
            return null;
        }

        for (ICdcJsonEnhancer enhancer : enhancers) {
            if (enhancer.canHandle(valueNode)) {
                log.debug("Detected CDC format: {}", enhancer.getFormatName());
                return enhancer;
            }
        }

        log.warn("No CDC format enhancer found for JSON: {}", valueNode);
        return null;
    }

    /**
     * Register a custom CDC JSON enhancer. Duplicate configurations are ignored.
     *
     * @param config custom CDC configuration
     */
    public synchronized void registerCustomEnhancer(CustomCdcConfig config) {
        if (config == null || customEnhancers.containsKey(config)) {
            return;
        }
        CustomCdcJsonEnhancer enhancer = new CustomCdcJsonEnhancer(config);
        customEnhancers.put(config, enhancer);
        enhancers.add(enhancer);
        enhancers.sort(Comparator.comparingInt(ICdcJsonEnhancer::getPriority));
        log.info(
                "Registered custom CDC JSON enhancer (operation field: {}, data field: {})",
                config.getOperationTypeField(),
                config.getDataField());
    }

    /**
     * Get all registered enhancers
     *
     * @return List of enhancers sorted by priority
     */
    public List<ICdcJsonEnhancer> getEnhancers() {
        return new ArrayList<>(enhancers);
    }
}

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

package org.apache.seatunnel.connectors.seatunnel.pi.client;

import org.apache.seatunnel.connectors.seatunnel.pi.config.PIConfigHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** PI WebID batch resolver for batch resolving PI paths to WebID */
public class PIWebIdBatchResolver {

    private static final Logger log = LoggerFactory.getLogger(PIWebIdBatchResolver.class);

    private final PIHttpClient httpClient;
    private final PIConfigHelper config;
    private final int batchSize;

    public PIWebIdBatchResolver(PIHttpClient httpClient, PIConfigHelper config) {
        this.httpClient = httpClient;
        this.config = config;
        this.batchSize = config.getWebIdResolveBatchSize();
    }

    /**
     * Batch resolve PI Paths to WebIDs. Optimization: use batch API to reduce network request count
     */
    public List<String> batchResolveWebIds(List<String> piPaths) {
        List<String> webIds = new ArrayList<>();

        // Process in batches to avoid oversized single requests
        for (int i = 0; i < piPaths.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, piPaths.size());
            List<String> batch = piPaths.subList(i, endIndex);

            try {
                List<String> batchWebIds = resolveBatch(batch);
                webIds.addAll(batchWebIds);

                log.info("Batch WebID resolution completed: {}/{}", endIndex, piPaths.size());

                // Avoid too frequent requests
                Thread.sleep(config.getWebIdResolveDelayMs());

            } catch (Exception e) {
                log.error("Batch WebID resolution failed, batch: {}-{}", i, endIndex, e);
                // Fallback to single resolution
                List<String> fallbackWebIds = fallbackSingleResolve(batch);
                webIds.addAll(fallbackWebIds);
            }
        }

        return webIds;
    }

    /** Batch resolve PI Paths to WebID mapping, including metadata information */
    public Map<String, PIWebIdMetadata> batchResolveWebIdMetadata(List<String> piPaths) {
        Map<String, PIWebIdMetadata> webIdMetadataMap = new HashMap<>();

        log.info(
                "Starting batch WebID metadata resolution, total paths: {}, batch size: {}",
                piPaths.size(),
                batchSize);

        // Process in batches to avoid oversized single requests
        for (int i = 0; i < piPaths.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, piPaths.size());
            List<String> batch = piPaths.subList(i, endIndex);

            try {
                Map<String, PIWebIdMetadata> batchMetadata = resolveBatchMetadata(batch);
                webIdMetadataMap.putAll(batchMetadata);

                // Avoid too frequent requests
                Thread.sleep(config.getWebIdResolveDelayMs());

            } catch (Exception e) {
                log.error("Batch WebID metadata resolution failed, batch: {}-{}", i, endIndex, e);
                // Fallback to single resolution
                Map<String, PIWebIdMetadata> fallbackMetadata =
                        fallbackSingleResolveMetadata(batch);
                webIdMetadataMap.putAll(fallbackMetadata);
            }
        }

        log.info("WebID metadata resolution completed, total count: {}", webIdMetadataMap.size());
        return webIdMetadataMap;
    }

    /** Batch resolve a batch of PI paths */
    private List<String> resolveBatch(List<String> piPaths) throws Exception {
        StringBuilder endpointBuilder = new StringBuilder();

        // Select API based on path type
        if (isAttributePath(piPaths.get(0))) {
            endpointBuilder.append("/piwebapi/attributes?");
        } else {
            endpointBuilder.append("/piwebapi/points?");
        }

        for (int i = 0; i < piPaths.size(); i++) {
            if (i > 0) endpointBuilder.append("&");
            endpointBuilder.append("path=").append(URLEncoder.encode(piPaths.get(i), "UTF-8"));
        }

        JsonNode response = httpClient.get(endpointBuilder.toString(), JsonNode.class);
        return extractWebIds(response);
    }

    /** Batch resolve a batch of PI path metadata */
    private Map<String, PIWebIdMetadata> resolveBatchMetadata(List<String> piPaths)
            throws Exception {
        StringBuilder endpointBuilder = new StringBuilder();

        // Select API based on path type
        if (isAttributePath(piPaths.get(0))) {
            endpointBuilder.append("/piwebapi/attributes?");
        } else {
            endpointBuilder.append("/piwebapi/points?");
        }

        for (int i = 0; i < piPaths.size(); i++) {
            if (i > 0) endpointBuilder.append("&");
            endpointBuilder.append("path=").append(URLEncoder.encode(piPaths.get(i), "UTF-8"));
        }

        JsonNode response = httpClient.get(endpointBuilder.toString(), JsonNode.class);
        return extractWebIdMetadata(response, piPaths);
    }

    /** Fallback to single resolution */
    private List<String> fallbackSingleResolve(List<String> piPaths) {
        List<String> webIds = new ArrayList<>();

        for (String path : piPaths) {
            try {
                String webId = resolveSingle(path);
                if (webId != null) {
                    webIds.add(webId);
                }

                // Single resolution also needs delay
                Thread.sleep(config.getWebIdResolveDelayMs());

            } catch (Exception e) {
                log.error("Single WebID resolution failed, path: {}", path, e);
            }
        }

        return webIds;
    }

    /** Fallback to single metadata resolution */
    private Map<String, PIWebIdMetadata> fallbackSingleResolveMetadata(List<String> piPaths) {
        Map<String, PIWebIdMetadata> metadataMap = new HashMap<>();

        for (String path : piPaths) {
            try {
                PIWebIdMetadata metadata = resolveSingleMetadata(path);
                if (metadata != null) {
                    metadataMap.put(metadata.getWebId(), metadata);
                }

                // Single resolution also needs delay
                Thread.sleep(config.getWebIdResolveDelayMs());

            } catch (Exception e) {
                log.error("Single WebID metadata resolution failed, path: {}", path, e);
            }
        }

        return metadataMap;
    }

    /** Resolve single PI path */
    private String resolveSingle(String piPath) throws Exception {
        StringBuilder endpointBuilder = new StringBuilder();

        if (isAttributePath(piPath)) {
            endpointBuilder.append("/piwebapi/attributes?");
        } else {
            endpointBuilder.append("/piwebapi/points?");
        }

        endpointBuilder.append("path=").append(URLEncoder.encode(piPath, "UTF-8"));

        JsonNode response = httpClient.get(endpointBuilder.toString(), JsonNode.class);
        List<String> webIds = extractWebIds(response);

        return webIds.isEmpty() ? null : webIds.get(0);
    }

    /** Resolve single PI path metadata */
    private PIWebIdMetadata resolveSingleMetadata(String piPath) throws Exception {
        StringBuilder endpointBuilder = new StringBuilder();

        if (isAttributePath(piPath)) {
            endpointBuilder.append("/piwebapi/attributes?");
        } else {
            endpointBuilder.append("/piwebapi/points?");
        }

        endpointBuilder.append("path=").append(URLEncoder.encode(piPath, "UTF-8"));

        JsonNode response = httpClient.get(endpointBuilder.toString(), JsonNode.class);
        Map<String, PIWebIdMetadata> metadataMap =
                extractWebIdMetadata(response, Arrays.asList(piPath));

        return metadataMap.isEmpty() ? null : metadataMap.values().iterator().next();
    }

    /** Extract WebID list from response */
    private List<String> extractWebIds(JsonNode response) {
        List<String> webIds = new ArrayList<>();

        if (response == null) {
            return webIds;
        }

        // Handle single object response
        if (response.has("WebId")) {
            webIds.add(response.get("WebId").asText());
        }

        // Handle array response
        if (response.has("Items") && response.get("Items").isArray()) {
            for (JsonNode item : response.get("Items")) {
                if (item.has("WebId")) {
                    webIds.add(item.get("WebId").asText());
                }
            }
        }

        return webIds;
    }

    /** Extract WebID metadata mapping from response */
    private Map<String, PIWebIdMetadata> extractWebIdMetadata(
            JsonNode response, List<String> originalPaths) {
        Map<String, PIWebIdMetadata> metadataMap = new HashMap<>();

        if (response == null) {
            log.warn("Response is null, returning empty metadata map");
            return metadataMap;
        }

        log.debug("Extracting metadata from response: {}", response.toString());

        // Handle single object response
        if (response.has("WebId")) {
            String webId = response.get("WebId").asText();
            String name =
                    response.has("Name")
                            ? response.get("Name").asText()
                            : extractNameFromPath(originalPaths.get(0));
            String path = originalPaths.get(0);
            log.info("Single object response - WebId: {}, Name: {}, Path: {}", webId, name, path);
            metadataMap.put(webId, new PIWebIdMetadata(webId, name, path));
        }

        // Handle array response
        if (response.has("Items") && response.get("Items").isArray()) {
            JsonNode items = response.get("Items");
            for (int i = 0; i < items.size() && i < originalPaths.size(); i++) {
                JsonNode item = items.get(i);
                if (item.has("WebId")) {
                    String webId = item.get("WebId").asText();
                    String name =
                            item.has("Name")
                                    ? item.get("Name").asText()
                                    : extractNameFromPath(originalPaths.get(i));
                    String path = originalPaths.get(i);
                    metadataMap.put(webId, new PIWebIdMetadata(webId, name, path));
                }
            }
        }

        return metadataMap;
    }

    /** Extract name from PI path */
    private String extractNameFromPath(String piPath) {
        if (piPath == null || piPath.isEmpty()) {
            log.warn("PI path is null or empty, returning Unknown");
            return "Unknown";
        }

        // For AF Attribute path: \\PI-AFServer01&02\WebAPI\Test|Attribute1
        if (piPath.contains("|")) {
            String[] parts = piPath.split("\\|");
            String name = parts.length > 1 ? parts[parts.length - 1] : "Unknown";
            log.info("AF Attribute path detected, extracted name: {}", name);
            return name;
        }

        // For PI Point path: \\pims.huafeng.com\HF.AA.NAB:LIA-26101.PV
        if (piPath.contains("\\")) {
            String[] parts = piPath.split("\\\\");
            String name = parts.length > 0 ? parts[parts.length - 1] : "Unknown";
            log.info("PI Point path detected, extracted name: {}", name);
            return name;
        }

        log.info("No special path pattern detected, using full path as name: {}", piPath);
        return piPath;
    }

    /** Determine if it's an AF Attribute path */
    private boolean isAttributePath(String path) {
        return path.contains("|");
    }
}

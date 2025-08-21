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

package org.apache.seatunnel.connectors.seatunnel.pi.source;

import org.apache.seatunnel.api.source.Collector;
import org.apache.seatunnel.api.source.SourceReader;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.pi.client.PIHttpClient;
import org.apache.seatunnel.connectors.seatunnel.pi.client.PIWebIdBatchResolver;
import org.apache.seatunnel.connectors.seatunnel.pi.client.PIWebIdMetadata;
import org.apache.seatunnel.connectors.seatunnel.pi.client.PIWebIdResolver;
import org.apache.seatunnel.connectors.seatunnel.pi.config.PIConfigHelper;
import org.apache.seatunnel.connectors.seatunnel.pi.exception.PIConnectorException;
import org.apache.seatunnel.connectors.seatunnel.pi.exception.PIErrorCode;
import org.apache.seatunnel.connectors.seatunnel.pi.split.PICheckpointState;
import org.apache.seatunnel.connectors.seatunnel.pi.split.PISplit;
import org.apache.seatunnel.connectors.seatunnel.pi.utils.PIDataTypeConverter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** PI Data Source Reader - Unified management of read modes */
@Slf4j
public class PISourceReader implements SourceReader<SeaTunnelRow, PISplit> {

    // ==================== Core Configuration ====================
    private final SourceReader.Context readerContext;
    private final PIConfigHelper configHelper;
    private final SeaTunnelRowType rowType;

    // ==================== Core Components ====================
    private PIHttpClient httpClient;
    private PIWebIdResolver webIdResolver;
    private PICheckpointState checkpointState;

    // ==================== Split and Data Management ====================
    private final List<PISplit> assignedSplits = new CopyOnWriteArrayList<>();
    private final BlockingQueue<SeaTunnelRow> dataBufferBlockQueue =
            new LinkedBlockingQueue<>(10000);

    // ==================== Batch Processing State ====================
    private LocalDateTime batchStartTime;
    private LocalDateTime batchEndTime;
    private LocalDateTime currentBatchStart;
    private LocalDateTime currentBatchEnd;
    private int batchWindowMinutes;
    private boolean queryCompleted = false;

    // ==================== WebID Management ====================
    // Primary: PI Path to WebID mapping
    private final Map<String, String> piPathToWebIdMap = new HashMap<>();
    // Resolved WebIDs for data querying
    private List<String> resolvedWebIds = new ArrayList<>();
    // WebID metadata for enhanced functionality
    private Map<String, PIWebIdMetadata> webIdMetadataMap = new HashMap<>();

    // ==================== Execution State ====================
    private volatile boolean initialized = false;
    private volatile boolean noMoreSplitsAssignment = false;

    public PISourceReader(
            PIConfigHelper configHelper,
            SeaTunnelRowType rowType,
            SourceReader.Context readerContext) {
        this.readerContext = readerContext;
        this.configHelper = configHelper;
        this.rowType = rowType;
        this.checkpointState = new PICheckpointState();
    }

    @Override
    public void open() throws Exception {

        try {
            // Only perform basic resource initialization, not dependent on splits
            httpClient = new PIHttpClient(configHelper);

            // Initialize WebID resolver
            webIdResolver = new PIWebIdResolver(configHelper, httpClient);

        } catch (Exception e) {
            log.error("Failed to open PI data source reader", e);
            throw e;
        }
    }

    @Override
    public void close() throws IOException {

        try {
            // Clear data buffer
            dataBufferBlockQueue.clear();

            // Close HTTP client
            if (httpClient != null) {
                httpClient.close();
            }

        } catch (Exception e) {
            log.error("Error occurred while closing PI data source reader", e);
            throw new IOException("Failed to close reader", e);
        }
    }

    /**
     * Generate next batch of data
     *
     * @param output output collector.
     * @throws Exception if error occurs.
     */
    @Override
    public void pollNext(Collector<SeaTunnelRow> output) throws Exception {
        // Process data based on read mode
        synchronized (output.getCheckpointLock()) {
            // Check batch mode end conditions
            if (noMoreSplitsAssignment && assignedSplits.isEmpty()) {
                // Readers without splits directly send end signal
                readerContext.signalNoMoreElement();
                return;
            }

            // Wait for split assignment
            if (assignedSplits.isEmpty()) {
                // Use configurable wait time instead of hard-coded value
                TimeUnit.MILLISECONDS.sleep(100);
                return;
            }

            // Initialize reader when splits are available (execute only once)
            if (!initialized) {
                initializeReaders();
                initialized = true;
                log.info("Reader initialization completed");
            }

            // Inline batch data reading logic
            pollNextBatchData(output);

            // Batch mode needs to check if data reading is complete
            if (!hasMoreBatchData()) {

                readerContext.signalNoMoreElement();
            }
        }
    }

    @Override
    public List<PISplit> snapshotState(long checkpointId) throws Exception {

        // Update checkpoint ID
        checkpointState.setCheckpointId(checkpointId);
        // Return currently processing splits
        return new ArrayList<>(assignedSplits);
    }

    @Override
    public void addSplits(List<PISplit> splits) {
        if (splits == null || splits.isEmpty()) {
            log.warn(
                    "Received empty or null splits assignment for Reader: {}",
                    readerContext.getIndexOfSubtask());
            return;
        }

        // CopyOnWriteArrayList is thread-safe, no need for additional synchronization
        assignedSplits.addAll(splits);
    }

    @Override
    public void handleNoMoreSplits() {
        noMoreSplitsAssignment = true;
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) throws Exception {
        log.debug(
                "Checkpoint completion notification - checkpointId: {}, Reader: {}",
                checkpointId,
                readerContext.getIndexOfSubtask());

        checkpointState.setCheckpointId(checkpointId);
    }

    /**
     * Initialize readers based on assigned splits
     *
     * <p>This method is called once when splits are available and before data processing begins.
     * The initialization sequence includes:
     *
     * @throws PIConnectorException when initialization fails
     * @throws Exception when underlying system errors occur
     */
    private void initializeReaders() throws Exception {
        log.info("Initialize readers based on splits, split count: {}", assignedSplits.size());

        // Performance optimization: perform WebID resolution only after split assignment to reduce
        // HTTP server pressure
        preResolveWebIdsOptimized();

        if (resolvedWebIds.isEmpty()) {
            throw new PIConnectorException(
                    PIErrorCode.INTERNAL_ERROR, "Resolved WebIDs list is empty");
        }

        initializeBatchMode();
    }

    /** Parse timestamp string to LocalDateTime, correctly handling timezone information */
    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null) {
            return null;
        }

        try {
            // Use PIDataTypeConverter's correct timezone handling logic
            if (timestamp.endsWith("Z")) {
                // UTC time, needs to be converted to system default timezone
                ZonedDateTime zdt =
                        ZonedDateTime.parse(
                                timestamp,
                                DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.of("UTC")));
                return zdt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } else {
                // Try multiple time formats
                try {
                    // Standard ISO format: 2024-06-20T09:00:00
                    return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME);
                } catch (DateTimeParseException e1) {
                    try {
                        // Space-separated format: 2024-06-20 09:00:00
                        return LocalDateTime.parse(
                                timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    } catch (DateTimeParseException e2) {
                        // Other possible formats
                        return LocalDateTime.parse(
                                timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                    }
                }
            }
        } catch (DateTimeParseException e) {
            log.error(
                    "Unable to parse timestamp: {}, supported formats: 'yyyy-MM-dd HH:mm:ss' or 'yyyy-MM-ddTHH:mm:ss' or 'yyyy-MM-ddTHH:mm:ssZ'",
                    timestamp,
                    e);
            return null;
        }
    }

    /**
     * Optimized WebID resolution based on splits
     *
     * <p>Optimization strategies: 1. Delayed execution until after split assignment to avoid
     * invalid resolution by readers without splits 2. Prioritize using pre-resolved WebIDs in
     * splits to reduce HTTP requests 3. Only resolve pi_paths actually needed by current Reader to
     * reduce server pressure
     */
    private void preResolveWebIdsOptimized() throws Exception {

        piPathToWebIdMap.clear();
        resolvedWebIds.clear();

        // Strategy 1: Prioritize extracting pre-resolved WebIDs from splits
        if (tryExtractWebIdsFromSplits()) {
            return;
        }

        // Strategy 2: fallback to traditional resolution method, prioritize PI Paths
        List<String> configPaths = configHelper.getPiPaths();
        List<String> configWebIds = configHelper.getWebIds();

        // Primary: Process PI Paths (most common user scenario)
        if (configPaths != null && !configPaths.isEmpty()) {
            resolvePiPathsOptimized(configPaths);
        }

        // Fallback: Process directly configured WebIDs (rare scenario)
        if (configWebIds != null && !configWebIds.isEmpty()) {
            addDirectWebIds(configWebIds);
        }

        if (resolvedWebIds.isEmpty()) {
            throw new PIConnectorException(
                    PIErrorCode.CONFIG_MISSING_TAG_PATHS,
                    "No valid PI Paths or WebIDs configured. Please configure pi_paths for normal usage.");
        }

        log.info("WebID resolution completed - Total: {} WebIDs", resolvedWebIds.size());
    }

    /**
     * Try to extract pre-resolved WebIDs from splits
     *
     * @return true if successfully extracted WebIDs from splits
     */
    private boolean tryExtractWebIdsFromSplits() {
        if (assignedSplits.isEmpty()) {

            return false;
        }

        Set<String> splitWebIds = new HashSet<>();
        for (PISplit split : assignedSplits) {
            if (split.getWebIds() != null && !split.getWebIds().isEmpty()) {
                splitWebIds.addAll(split.getWebIds());
            }
        }

        if (splitWebIds.isEmpty()) {

            return false;
        }

        // Use WebIDs from splits, but need to rebuild mapping relationships
        resolvedWebIds.addAll(splitWebIds);

        // Rebuild mapping relationships and create metadata objects
        for (String webId : splitWebIds) {
            piPathToWebIdMap.put(webId, webId);
            // Create basic metadata objects to avoid webIdMetadataMap.get(webId) returning null
            PIWebIdMetadata metadata = new PIWebIdMetadata(webId, webId, webId);
            webIdMetadataMap.put(webId, metadata);
        }

        return true;
    }

    /**
     * Optimized version of PI Paths resolution - utilize cache to reduce HTTP requests while
     * obtaining metadata
     */
    private void resolvePiPathsOptimized(List<String> configPaths) throws PIConnectorException {

        try {
            // Use PIWebIdBatchResolver to obtain metadata information
            PIWebIdBatchResolver batchResolver = new PIWebIdBatchResolver(httpClient, configHelper);
            webIdMetadataMap = batchResolver.batchResolveWebIdMetadata(configPaths);

            // Build traditional mapping relationships to maintain compatibility
            for (PIWebIdMetadata metadata : webIdMetadataMap.values()) {
                String webId = metadata.getWebId();
                String path = metadata.getPath();
                piPathToWebIdMap.put(path, webId);
                resolvedWebIds.add(webId);
            }

            log.info(
                    "Successfully resolved {} Paths to WebIDs (with metadata)",
                    webIdMetadataMap.size());
        } catch (Exception e) {
            log.error("Failed to resolve PI Paths", e);
            throw new PIConnectorException(
                    PIErrorCode.WEBID_RESOLUTION_FAILED,
                    "Failed to resolve PI Paths: " + e.getMessage(),
                    e);
        }
    }

    /** Resolve PI Paths */
    private void resolvePiPaths(List<String> configPaths) throws PIConnectorException {

        try {
            Map<String, String> resolved = webIdResolver.resolveWebIdsAsMap(configPaths);
            piPathToWebIdMap.putAll(resolved);
            resolvedWebIds.addAll(resolved.values());

        } catch (Exception e) {
            log.error("Failed to resolve PI Paths", e);
            throw new PIConnectorException(
                    PIErrorCode.WEBID_RESOLUTION_FAILED,
                    "Failed to resolve PI Paths: " + e.getMessage(),
                    e);
        }
    }

    /**
     * Add directly configured WebIDs
     *
     * <p>Note: Most users should use pi_paths instead of direct WebIDs. This method is provided for
     * advanced users who already know the WebIDs.
     */
    private void addDirectWebIds(List<String> configWebIds) {

        int addedCount = 0;
        for (String webId : configWebIds) {
            if (webId != null && !webId.trim().isEmpty() && !resolvedWebIds.contains(webId)) {
                resolvedWebIds.add(webId);
                piPathToWebIdMap.put(webId, webId);

                // Create basic metadata for direct WebIDs
                PIWebIdMetadata metadata = new PIWebIdMetadata(webId, webId, webId);
                webIdMetadataMap.put(webId, metadata);
                addedCount++;
            }
        }
    }

    // ==================== Batch Data Reading Methods ====================

    /** Poll batch data */
    private void pollNextBatchData(Collector<SeaTunnelRow> output) throws Exception {
        // Check if need to query next batch of data: buffer is empty & query not completed
        if (dataBufferBlockQueue.isEmpty() && !queryCompleted) {
            fetchNextBatch();
        }

        // Batch retrieve data from buffer and output
        List<SeaTunnelRow> rows = new ArrayList<>();
        int drainedCount = dataBufferBlockQueue.drainTo(rows, 100);

        for (SeaTunnelRow row : rows) {
            output.collect(row);
        }

        // If no data, sleep briefly to avoid CPU spinning
        if (drainedCount == 0) {
            Thread.sleep(100);
        }
    }

    /** Query next batch of data */
    private void fetchNextBatch() throws Exception {
        // Check if reached or exceeded end time
        if (!currentBatchStart.isBefore(batchEndTime)) {
            queryCompleted = true;

            return;
        }

        try {
            JsonNode response = queryBatchData();
            List<SeaTunnelRow> rows = parseResponseToRows(response);

            int recordCount = addRowsToBuffer(rows);
            log.debug("Batch query completed - retrieved {} records", recordCount);

            // Update next batch time range
            moveToNextBatch();

        } catch (Exception e) {
            log.error("Batch query failed", e);
            throw new PIConnectorException(
                    PIErrorCode.HTTP_REQUEST_FAILED, "Batch query failed: " + e.getMessage(), e);
        }
    }

    /** Calculate batch end time */
    private LocalDateTime calculateBatchEnd(LocalDateTime batchStart) {
        LocalDateTime nextBatchEnd = batchStart.plusMinutes(batchWindowMinutes);
        return nextBatchEnd.isAfter(batchEndTime) ? batchEndTime : nextBatchEnd;
    }

    /** Whether there is more batch data */
    private boolean hasMoreBatchData() {
        return !queryCompleted || !dataBufferBlockQueue.isEmpty();
    }

    // ==================== Helper Methods ====================

    /** Initialize batch mode */
    private void initializeBatchMode() throws PIConnectorException {
        // Check if start_time is provided (required)
        if (configHelper.getStartTime() == null) {
            throw new PIConnectorException(
                    PIErrorCode.CONFIG_VALIDATION_FAILED, "Batch mode must specify start time");
        }

        LocalDateTime startTime = configHelper.getParsedStartTime();
        int windowMinutes = configHelper.getBatchWindowMinutes();

        // Check start time parsing result
        if (startTime == null) {
            throw new PIConnectorException(
                    PIErrorCode.CONFIG_VALIDATION_FAILED,
                    "Unable to parse start time: " + configHelper.getStartTime());
        }

        // Handle end_time (optional, defaults to start_time + 1 minute if not provided)
        LocalDateTime endTime;
        if (configHelper.getEndTime() != null) {
            endTime = configHelper.getParsedEndTime();
            if (endTime == null) {
                throw new PIConnectorException(
                        PIErrorCode.CONFIG_VALIDATION_FAILED,
                        "Unable to parse end time: " + configHelper.getEndTime());
            }
        } else {
            // Default: start_time + 1 minute
            endTime = startTime.plusMinutes(1);
            log.info("End time not specified, using default: start_time + 1 minute = {}", endTime);
        }

        // Check time range validity
        if (startTime.isAfter(endTime)) {
            throw new PIConnectorException(
                    PIErrorCode.CONFIG_VALIDATION_FAILED,
                    "Start time cannot be later than end time");
        }

        // Initialize batch processing variables
        batchStartTime = startTime;
        batchEndTime = endTime;
        batchWindowMinutes = windowMinutes;
        currentBatchStart = batchStartTime;
        currentBatchEnd = calculateBatchEnd(currentBatchStart);
    }

    /** Query batch data */
    private JsonNode queryBatchData() throws Exception {
        String startTimeUtc = formatToUtcTime(currentBatchStart);
        String endTimeUtc = formatToUtcTime(currentBatchEnd);

        // New API format requires individual calls for each webId
        if (resolvedWebIds.size() == 1) {
            // Single webId case, need to add webId information to response
            String webId = resolvedWebIds.get(0);
            JsonNode response =
                    httpClient.queryRecordedData(
                            webId,
                            startTimeUtc,
                            endTimeUtc,
                            configHelper.getMaxCount(),
                            configHelper.getBoundaryType());

            // Add webId information to each item in response
            return addWebIdToResponse(response, webId);
        } else {
            // Multiple webIds case: merge results
            return queryMultipleWebIds(startTimeUtc, endTimeUtc);
        }
    }

    /** Add webId, name and path information to each item in response */
    private JsonNode addWebIdToResponse(JsonNode response, String webId) {
        if (response == null || !response.has("Items") || !response.get("Items").isArray()) {
            return response;
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        result.setAll((ObjectNode) response);

        ArrayNode enhancedItems = mapper.createArrayNode();
        ArrayNode originalItems = (ArrayNode) response.get("Items");

        // Get metadata information
        PIWebIdMetadata metadata = webIdMetadataMap.get(webId);
        String name = metadata != null ? metadata.getName() : "Unknown";
        String path = metadata != null ? metadata.getPath() : "Unknown";

        for (JsonNode item : originalItems) {
            ObjectNode enhancedItem = mapper.createObjectNode();
            enhancedItem.setAll((ObjectNode) item);
            enhancedItem.put("WebId", webId);
            enhancedItem.put("Name", name);
            enhancedItem.put("Path", path);
            enhancedItems.add(enhancedItem);
        }

        result.set("Items", enhancedItems);
        return result;
    }

    /** Query data for multiple WebIDs and merge results */
    private JsonNode queryMultipleWebIds(String startTimeUtc, String endTimeUtc) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode mergedResult = mapper.createObjectNode();
        ArrayNode mergedItems = mapper.createArrayNode();

        for (String webId : resolvedWebIds) {
            try {
                JsonNode response =
                        httpClient.queryRecordedData(
                                webId,
                                startTimeUtc,
                                endTimeUtc,
                                configHelper.getMaxCount(),
                                configHelper.getBoundaryType());

                // Merge Items array and add webId, name and path information to each item
                if (response.has("Items") && response.get("Items").isArray()) {
                    ArrayNode items = (ArrayNode) response.get("Items");

                    // Get metadata information
                    PIWebIdMetadata metadata = webIdMetadataMap.get(webId);
                    String name = metadata != null ? metadata.getName() : "Unknown";
                    String path = metadata != null ? metadata.getPath() : "Unknown";

                    for (JsonNode item : items) {
                        // Create new item node, add webId, name and path information
                        ObjectNode enhancedItem = mapper.createObjectNode();
                        enhancedItem.setAll((ObjectNode) item);
                        enhancedItem.put("WebId", webId);
                        enhancedItem.put("Name", name);
                        enhancedItem.put("Path", path);
                        mergedItems.add(enhancedItem);
                    }
                }
            } catch (Exception e) {
                log.warn("Query WebID {} failed: {}", webId, e.getMessage());
                // Continue processing other webIds
            }
        }

        mergedResult.set("Items", mergedItems);
        return mergedResult;
    }

    /** Parse response to row data */
    private List<SeaTunnelRow> parseResponseToRows(JsonNode response) throws Exception {
        return PIDataTypeConverter.parseBatchResponse(
                response.toString(), rowType, configHelper.getJsonField());
    }

    /** Add row data to buffer */
    private int addRowsToBuffer(List<SeaTunnelRow> rows) throws InterruptedException {
        for (SeaTunnelRow row : rows) {
            dataBufferBlockQueue.put(row);
        }
        return rows.size();
    }

    /** Move to next batch */
    private void moveToNextBatch() {
        currentBatchStart = currentBatchEnd;
        currentBatchEnd = calculateBatchEnd(currentBatchStart);
    }

    /** Format time to UTC format */
    private String formatToUtcTime(LocalDateTime localTime) {
        // Convert to UTC time and format as required by PI Web API
        ZonedDateTime utcTime =
                localTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC"));

        // Use standard ISO format, PI Web API accepts this format
        return utcTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    }
}

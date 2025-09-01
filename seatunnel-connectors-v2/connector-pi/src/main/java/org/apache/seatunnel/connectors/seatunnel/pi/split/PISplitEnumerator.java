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

package org.apache.seatunnel.connectors.seatunnel.pi.split;

import org.apache.seatunnel.api.source.SourceSplitEnumerator;
import org.apache.seatunnel.connectors.seatunnel.pi.client.PIHttpClient;
import org.apache.seatunnel.connectors.seatunnel.pi.client.PIWebIdBatchResolver;
import org.apache.seatunnel.connectors.seatunnel.pi.config.PIConfigHelper;
import org.apache.seatunnel.connectors.seatunnel.pi.exception.PIConnectorException;
import org.apache.seatunnel.connectors.seatunnel.pi.exception.PIErrorCode;
import org.apache.seatunnel.connectors.seatunnel.pi.utils.PIPathValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** PI Batch Data Source Split Enumerator */
public class PISplitEnumerator implements SourceSplitEnumerator<PISplit, PICheckpointState> {

    private static final Logger log = LoggerFactory.getLogger(PISplitEnumerator.class);

    private final SourceSplitEnumerator.Context<PISplit> context;
    private final PIConfigHelper configHelper;
    private final Map<Integer, List<PISplit>> pendingSplits;
    private final Object stateLock = new Object();

    private PIHttpClient httpClient;
    private PIWebIdBatchResolver webIdResolver;
    private PISplitStrategy splitStrategy;

    public PISplitEnumerator(
            PIConfigHelper configHelper, SourceSplitEnumerator.Context<PISplit> context) {
        this(configHelper, context, null);
    }

    public PISplitEnumerator(
            PIConfigHelper configHelper,
            SourceSplitEnumerator.Context<PISplit> context,
            PICheckpointState checkpointState) {
        this.context = context;
        this.configHelper = configHelper;
        this.pendingSplits = new HashMap<>();

        initializeComponents();
    }

    private void initializeComponents() {
        try {
            this.httpClient = new PIHttpClient(configHelper);
            this.webIdResolver = new PIWebIdBatchResolver(httpClient, configHelper);
            this.splitStrategy = new PISplitStrategy();

            log.info("PI components initialization completed");
        } catch (Exception e) {
            log.error("PI components initialization failed", e);
            throw new PIConnectorException(
                    PIErrorCode.INITIALIZATION_FAILED, "PI components initialization failed", e);
        }
    }

    @Override
    public void open() {
        List<String> piPaths = configHelper.getPiPaths();
        if (piPaths == null || piPaths.isEmpty()) {
            log.warn("PI path list is empty, cannot create splits");
            return;
        }

        log.info("PI batch connector startup - Total PI paths: {}", piPaths.size());
        initializeSplits(piPaths);
    }

    private void initializeSplits(List<String> piPaths) {
        try {
            // Validate and resolve paths
            PIPathValidator.validatePiPaths(piPaths);
            List<String> resolvedWebIds = webIdResolver.batchResolveWebIds(piPaths);

            // Create splits using strategy
            List<PISplit> splits =
                    splitStrategy.createSplits(
                            resolvedWebIds,
                            configHelper.getStartDateTime(),
                            configHelper.getEndDateTime(),
                            configHelper.getWebIdsPerSplit());

            // Distribute splits using round-robin
            synchronized (stateLock) {
                distributeSplits(splits);
            }

            log.info(
                    "Created {} splits for {} WebIDs, time range: {} - {}",
                    splits.size(),
                    resolvedWebIds.size(),
                    configHelper.getStartTime(),
                    configHelper.getEndTime());

        } catch (Exception e) {
            log.error("Split initialization failed", e);
            throw new PIConnectorException(
                    PIErrorCode.INITIALIZATION_FAILED, "Split initialization failed", e);
        }
    }

    private void distributeSplits(List<PISplit> splits) {
        int parallelism = context.currentParallelism();
        for (int i = 0; i < splits.size(); i++) {
            int readerId = i % parallelism;
            pendingSplits.computeIfAbsent(readerId, k -> new ArrayList<>()).add(splits.get(i));
        }
    }

    /**
     * Reader registration handling method - New Reader entry point
     *
     * @param subtaskId Registered Reader ID
     */
    @Override
    public void registerReader(int subtaskId) {
        log.info("Register reader {}", subtaskId);

        synchronized (stateLock) {
            List<PISplit> readerSplits = pendingSplits.get(subtaskId);
            if (readerSplits != null && !readerSplits.isEmpty()) {
                context.assignSplit(subtaskId, new ArrayList<>(readerSplits));
                readerSplits.clear();
                pendingSplits.remove(subtaskId);

                context.signalNoMoreSplits(subtaskId);
                log.info("Assigned splits to reader {}", subtaskId);
            }
        }
    }

    /**
     * Core execution method for split creation and assignment
     *
     * @throws Exception Exception during split creation or assignment process
     */
    @Override
    public void run() throws Exception {
        Set<Integer> readers = context.registeredReaders();

        synchronized (stateLock) {
            assignPendingSplits(readers);
        }

        // Signal completion to all readers
        readers.forEach(context::signalNoMoreSplits);
    }

    private void assignPendingSplits(Set<Integer> readers) {
        for (Map.Entry<Integer, List<PISplit>> entry : pendingSplits.entrySet()) {
            int readerId = entry.getKey();
            List<PISplit> splits = entry.getValue();

            if (readers.contains(readerId) && !splits.isEmpty()) {
                context.assignSplit(readerId, splits);
                log.info("Assigned {} splits to reader {}", splits.size(), readerId);
            }
        }
        pendingSplits.clear();
    }

    /**
     * Split recovery handling method - Core mechanism for fault recovery
     *
     * @param splits List of splits to be recovered
     * @param subtaskId Reader ID returning the splits
     */
    @Override
    public void addSplitsBack(List<PISplit> splits, int subtaskId) {
        if (!splits.isEmpty()) {
            log.info("Adding {} splits back from subtask {}", splits.size(), subtaskId);

            synchronized (stateLock) {
                pendingSplits.computeIfAbsent(subtaskId, k -> new ArrayList<>()).addAll(splits);

                if (context.registeredReaders().contains(subtaskId)) {
                    context.assignSplit(subtaskId, splits);
                }
            }
        }
    }

    /**
     * Get current total number of unassigned splits
     *
     * @return Total number of unassigned splits
     */
    @Override
    public int currentUnassignedSplitSize() {
        synchronized (stateLock) {
            return pendingSplits.values().stream().mapToInt(List::size).sum();
        }
    }

    /**
     * Handle Reader on-demand split requests - Dynamic split assignment mechanism
     *
     * @param subtaskId Reader ID requesting splits
     */
    @Override
    public void handleSplitRequest(int subtaskId) {
        log.debug("Handle split request from subtask {}", subtaskId);

        synchronized (stateLock) {
            List<PISplit> readerSplits = pendingSplits.get(subtaskId);
            if (readerSplits != null && !readerSplits.isEmpty()) {
                PISplit split = readerSplits.remove(0);
                context.assignSplit(subtaskId, Collections.singletonList(split));

                if (readerSplits.isEmpty()) {
                    pendingSplits.remove(subtaskId);
                }
            }
        }
    }

    /**
     * Checkpoint state snapshot method - Fault recovery state saving
     *
     * @param checkpointId Checkpoint ID
     * @return State snapshot object
     * @throws Exception State serialization exception
     */
    @Override
    public PICheckpointState snapshotState(long checkpointId) throws Exception {
        synchronized (stateLock) {
            log.debug("Creating snapshot for checkpoint {}", checkpointId);
            PICheckpointState snapshot = new PICheckpointState();
            snapshot.setCheckpointId(checkpointId);
            return snapshot;
        }
    }

    /**
     * Checkpoint completion notification processing method
     *
     * @param checkpointId Completed checkpoint ID
     * @throws Exception State update exception
     */
    @Override
    public void notifyCheckpointComplete(long checkpointId) throws Exception {
        log.debug("Checkpoint {} completed", checkpointId);
    }

    /**
     * Enumerator shutdown and resource cleanup method
     *
     * @throws IOException IO exception if resource cleanup fails
     */
    @Override
    public void close() throws IOException {
        log.info("PI split enumerator closing...");

        try {
            synchronized (stateLock) {
                pendingSplits.clear();
            }

            // Close HTTP client to prevent resource leak
            if (httpClient != null) {
                httpClient.close();
                log.debug("HTTP client closed successfully");
            }

            log.info("PI split enumerator closed successfully");
        } catch (Exception e) {
            log.error("Error occurred while closing PI split enumerator", e);
            throw new IOException("Failed to close PI enumerator resources", e);
        }
    }
}

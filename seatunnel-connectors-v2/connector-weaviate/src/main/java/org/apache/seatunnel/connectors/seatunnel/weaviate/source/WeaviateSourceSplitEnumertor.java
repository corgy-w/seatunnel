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

package org.apache.seatunnel.connectors.seatunnel.weaviate.source;

import org.apache.seatunnel.api.source.SourceSplitEnumerator;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.TablePath;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WeaviateSourceSplitEnumertor
        implements SourceSplitEnumerator<WeaviateSourceSplit, WeaviateSourceState> {

    private final Map<TablePath, CatalogTable> tables;
    private final Context<WeaviateSourceSplit> context;
    private final ConcurrentLinkedQueue<TablePath> pendingTables;
    private final Map<Integer, List<WeaviateSourceSplit>> pendingSplits;
    private final Object stateLock = new Object();

    public WeaviateSourceSplitEnumertor(
            Context<WeaviateSourceSplit> context,
            Map<TablePath, CatalogTable> sourceTables,
            WeaviateSourceState sourceState) {
        this.tables = sourceTables;
        this.context = context;
        if (sourceState == null) {
            this.pendingTables = new ConcurrentLinkedQueue<>(tables.keySet());
            this.pendingSplits = new HashMap<>();
        } else {
            this.pendingTables = new ConcurrentLinkedQueue<>(sourceState.getPendingTables());
            this.pendingSplits = new HashMap<>(sourceState.getPendingSplits());
        }
    }

    @Override
    public void open() {}

    @Override
    public void run() throws Exception {}

    @Override
    public void close() throws IOException {}

    @Override
    public void addSplitsBack(List<WeaviateSourceSplit> splits, int subtaskId) {}

    @Override
    public int currentUnassignedSplitSize() {
        return 0;
    }

    @Override
    public void handleSplitRequest(int subtaskId) {}

    @Override
    public void registerReader(int subtaskId) {}

    @Override
    public WeaviateSourceState snapshotState(long checkpointId) throws Exception {
        return null;
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) throws Exception {}
}

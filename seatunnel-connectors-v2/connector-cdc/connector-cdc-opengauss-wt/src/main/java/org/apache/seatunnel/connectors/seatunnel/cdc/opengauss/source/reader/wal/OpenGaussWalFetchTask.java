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

package org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.source.reader.wal;

import org.apache.seatunnel.connectors.cdc.base.source.reader.external.FetchTask;
import org.apache.seatunnel.connectors.cdc.base.source.split.IncrementalSplit;
import org.apache.seatunnel.connectors.cdc.base.source.split.SourceSplitBase;
import org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.source.reader.OpenGaussSourceFetchTaskContext;

import io.debezium.connector.opengauss.OpengaussOffsetContext;
import io.debezium.connector.opengauss.OpengaussStreamingChangeEventSource;
import io.debezium.connector.postgresql.PostgresOffsetContext;
import io.debezium.connector.postgresql.connection.Lsn;
import io.debezium.pipeline.source.spi.ChangeEventSource;
import io.debezium.util.Clock;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class OpenGaussWalFetchTask implements FetchTask<SourceSplitBase> {
    private final IncrementalSplit split;
    private volatile boolean taskRunning = false;
    private Long lastCommitLsn;
    private OpengaussStreamingChangeEventSource streamingChangeEventSource;
    private OpengaussOffsetContext offsetContext;

    public OpenGaussWalFetchTask(IncrementalSplit split) {
        this.split = split;
    }

    @Override
    public void execute(Context context) throws Exception {
        OpenGaussSourceFetchTaskContext sourceFetchContext =
                (OpenGaussSourceFetchTaskContext) context;
        taskRunning = true;

        streamingChangeEventSource =
                new OpengaussStreamingChangeEventSource(
                        sourceFetchContext.getDbzConnectorConfig(),
                        sourceFetchContext.getSnapshotter(),
                        sourceFetchContext.getDataConnection(),
                        sourceFetchContext.getOpenGaussEventDispatcher(),
                        sourceFetchContext.getErrorHandler(),
                        Clock.SYSTEM,
                        sourceFetchContext.getDatabaseSchema(),
                        sourceFetchContext.getTaskContext(),
                        sourceFetchContext.getReplicationConnection());

        offsetContext = sourceFetchContext.getOffsetContext();

        TransactionLogSplitChangeEventSourceContext changeEventSourceContext =
                new TransactionLogSplitChangeEventSourceContext();

        log.info(
                "Start streaming change event source for postgres wal split: {}",
                split.getStartupOffset().toString());

        streamingChangeEventSource.execute(changeEventSourceContext, offsetContext);
    }

    public void commitCurrentOffset() {
        if (streamingChangeEventSource != null && offsetContext != null) {

            // only extracting and storing the lsn of the last commit
            Long commitLsn =
                    (Long) offsetContext.getOffset().get(PostgresOffsetContext.LAST_COMMIT_LSN_KEY);
            if (commitLsn != null
                    && (lastCommitLsn == null
                            || Lsn.valueOf(commitLsn).compareTo(Lsn.valueOf(lastCommitLsn)) > 0)) {
                lastCommitLsn = commitLsn;

                Map<String, Object> offsets = new HashMap<>();
                offsets.put(PostgresOffsetContext.LAST_COMMIT_LSN_KEY, lastCommitLsn);
                log.info("Committing offset {} for {}", Lsn.valueOf(lastCommitLsn), split);
                streamingChangeEventSource.commitOffset(offsets);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return taskRunning;
    }

    @Override
    public void shutdown() {
        taskRunning = false;
    }

    @Override
    public SourceSplitBase getSplit() {
        return split;
    }

    private class TransactionLogSplitChangeEventSourceContext
            implements ChangeEventSource.ChangeEventSourceContext {
        @Override
        public boolean isRunning() {
            return taskRunning;
        }
    }
}

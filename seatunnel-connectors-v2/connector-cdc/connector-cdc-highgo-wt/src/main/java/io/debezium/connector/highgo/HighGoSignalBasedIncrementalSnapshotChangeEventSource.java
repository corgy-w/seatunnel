/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.highgo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.connector.highgo.connection.HighGoConnection;
import io.debezium.jdbc.JdbcConnection;
import io.debezium.pipeline.EventDispatcher;
import io.debezium.pipeline.source.snapshot.incremental.SignalBasedIncrementalSnapshotChangeEventSource;
import io.debezium.pipeline.source.spi.DataChangeEventListener;
import io.debezium.pipeline.source.spi.SnapshotProgressListener;
import io.debezium.relational.RelationalDatabaseConnectorConfig;
import io.debezium.relational.TableId;
import io.debezium.schema.DatabaseSchema;
import io.debezium.util.Clock;

/**
 * Custom HighGo implementation of the {@link SignalBasedIncrementalSnapshotChangeEventSource}
 * implementation which performs an explicit schema refresh of a table prior to the incremental
 * snapshot starting.
 *
 * @author Chris Cranford
 */
public class HighGoSignalBasedIncrementalSnapshotChangeEventSource
        extends SignalBasedIncrementalSnapshotChangeEventSource<HighGoPartition, TableId> {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(HighGoSignalBasedIncrementalSnapshotChangeEventSource.class);

    private final HighGoConnection jdbcConnection;
    private final HighGoSchema schema;

    public HighGoSignalBasedIncrementalSnapshotChangeEventSource(
            RelationalDatabaseConnectorConfig config,
            JdbcConnection jdbcConnection,
            EventDispatcher<HighGoPartition, TableId> dispatcher,
            DatabaseSchema<?> databaseSchema,
            Clock clock,
            SnapshotProgressListener<HighGoPartition> progressListener,
            DataChangeEventListener<HighGoPartition> dataChangeEventListener) {
        super(
                config,
                jdbcConnection,
                dispatcher,
                databaseSchema,
                clock,
                progressListener,
                dataChangeEventListener);
        this.jdbcConnection = (HighGoConnection) jdbcConnection;
        this.schema = (HighGoSchema) databaseSchema;
    }
}

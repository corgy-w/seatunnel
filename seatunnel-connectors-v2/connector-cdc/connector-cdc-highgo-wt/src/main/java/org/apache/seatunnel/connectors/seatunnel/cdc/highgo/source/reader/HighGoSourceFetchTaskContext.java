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

package org.apache.seatunnel.connectors.seatunnel.cdc.highgo.source.reader;

import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.cdc.base.config.JdbcSourceConfig;
import org.apache.seatunnel.connectors.cdc.base.dialect.JdbcDataSourceDialect;
import org.apache.seatunnel.connectors.cdc.base.relational.JdbcSourceEventDispatcher;
import org.apache.seatunnel.connectors.cdc.base.source.offset.Offset;
import org.apache.seatunnel.connectors.cdc.base.source.reader.external.JdbcSourceFetchTaskContext;
import org.apache.seatunnel.connectors.cdc.base.source.split.SourceSplitBase;
import org.apache.seatunnel.connectors.seatunnel.cdc.highgo.config.HighGoSourceConfig;
import org.apache.seatunnel.connectors.seatunnel.cdc.highgo.source.offset.LsnOffset;
import org.apache.seatunnel.connectors.seatunnel.cdc.highgo.utils.HighGoUtils;

import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;

import io.debezium.DebeziumException;
import io.debezium.connector.base.ChangeEventQueue;
import io.debezium.connector.highgo.HighGoConnectorConfig;
import io.debezium.connector.highgo.HighGoErrorHandler;
import io.debezium.connector.highgo.HighGoEventDispatcher;
import io.debezium.connector.highgo.HighGoEventMetadataProvider;
import io.debezium.connector.highgo.HighGoOffsetContext;
import io.debezium.connector.highgo.HighGoPartition;
import io.debezium.connector.highgo.HighGoSchema;
import io.debezium.connector.highgo.HighGoTaskContext;
import io.debezium.connector.highgo.HighGoTopicSelector;
import io.debezium.connector.highgo.TypeRegistry;
import io.debezium.connector.highgo.connection.HighGoConnection;
import io.debezium.connector.highgo.connection.ReplicationConnection;
import io.debezium.connector.highgo.spi.SlotState;
import io.debezium.connector.highgo.spi.Snapshotter;
import io.debezium.pipeline.DataChangeEvent;
import io.debezium.pipeline.ErrorHandler;
import io.debezium.pipeline.metrics.DefaultChangeEventSourceMetricsFactory;
import io.debezium.pipeline.metrics.SnapshotChangeEventSourceMetrics;
import io.debezium.relational.Table;
import io.debezium.relational.TableId;
import io.debezium.relational.Tables;
import io.debezium.relational.history.TableChanges;
import io.debezium.schema.TopicSelector;
import io.debezium.util.Clock;
import io.debezium.util.LoggingContext;
import io.debezium.util.Metronome;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;

import static org.apache.seatunnel.connectors.seatunnel.cdc.highgo.utils.HighGoConnectionUtils.newHighGoValueConverterBuilder;

@Slf4j
public class HighGoSourceFetchTaskContext extends JdbcSourceFetchTaskContext {

    private static final String CONTEXT_NAME = "highgo-cdc-connector-task";

    private final HighGoConnection dataConnection;

    @Getter private ReplicationConnection replicationConnection;

    private final HighGoEventMetadataProvider metadataProvider;

    @Getter private Snapshotter snapshotter;
    private HighGoSchema databaseSchema;
    private HighGoOffsetContext offsetContext;
    private HighGoPartition partition;
    private TopicSelector<TableId> topicSelector;
    private JdbcSourceEventDispatcher<HighGoPartition> dispatcher;
    private HighGoEventDispatcher<TableId> highGoEventDispatcher;
    private ChangeEventQueue<DataChangeEvent> queue;
    private HighGoErrorHandler errorHandler;

    @Getter private HighGoTaskContext taskContext;

    private SnapshotChangeEventSourceMetrics<HighGoPartition> snapshotChangeEventSourceMetrics;

    private HighGoConnection.HighGoValueConverterBuilder highGoValueConverterBuilder;

    private Collection<TableChanges.TableChange> engineHistory;

    public HighGoSourceFetchTaskContext(
            JdbcSourceConfig sourceConfig,
            JdbcDataSourceDialect dataSourceDialect,
            HighGoConnection dataConnection,
            Collection<TableChanges.TableChange> engineHistory) {
        super(sourceConfig, dataSourceDialect);
        this.dataConnection = dataConnection;
        this.metadataProvider = new HighGoEventMetadataProvider();
        this.engineHistory = engineHistory;
        this.highGoValueConverterBuilder = newHighGoValueConverterBuilder(getDbzConnectorConfig());
    }

    @Override
    public void configure(SourceSplitBase sourceSplitBase) {
        super.registerDatabaseHistory(sourceSplitBase, dataConnection);

        // initial stateful objects
        final HighGoConnectorConfig connectorConfig = getDbzConnectorConfig();
        this.snapshotter = connectorConfig.getSnapshotter();

        this.topicSelector = HighGoTopicSelector.create(connectorConfig);

        final TypeRegistry typeRegistry = dataConnection.getTypeRegistry();

        this.databaseSchema =
                new HighGoSchema(
                        connectorConfig,
                        typeRegistry,
                        topicSelector,
                        highGoValueConverterBuilder.build(typeRegistry));
        this.taskContext = new HighGoTaskContext(connectorConfig, databaseSchema, topicSelector);
        try {
            taskContext.refreshSchema(dataConnection, false);
        } catch (SQLException e) {
            throw new DebeziumException("load schema failed", e);
        }
        this.offsetContext =
                loadStartingOffsetState(
                        new HighGoOffsetContext.Loader(connectorConfig), sourceSplitBase);
        this.partition = new HighGoPartition(connectorConfig.getLogicalName());

        final int queueSize =
                sourceSplitBase.isSnapshotSplit() && isExactlyOnce()
                        ? Integer.MAX_VALUE
                        : getSourceConfig().getDbzConnectorConfig().getMaxQueueSize();

        LoggingContext.PreviousContext previousContext =
                taskContext.configureLoggingContext(CONTEXT_NAME);
        try {
            // Print out the server information
            SlotState slotInfo = null;
            try {
                if (log.isInfoEnabled()) {
                    log.info(dataConnection.serverInfo().toString());
                }
                slotInfo =
                        dataConnection.getReplicationSlotState(
                                connectorConfig.slotName(),
                                connectorConfig.plugin().getPostgresPluginName());
            } catch (SQLException e) {
                log.warn(
                        "unable to load info of replication slot, Debezium will try to create the slot");
            }

            if (offsetContext == null) {
                log.info("No previous offset found");
                // if we have no initial offset, indicate that to Snapshotter by passing null
                snapshotter.init(connectorConfig, null, slotInfo);
            } else {
                log.info("Found previous offset {}", offsetContext);
                snapshotter.init(connectorConfig, offsetContext.asOffsetState(), slotInfo);
            }

            if (snapshotter.shouldStream()) {
                // we need to create the slot before we start streaming if it doesn't exist
                // otherwise we can't stream back changes happening while the snapshot is taking
                // place
                if (replicationConnection == null) {
                    this.replicationConnection =
                            createReplicationConnection(
                                    this.taskContext,
                                    snapshotter.shouldSnapshot(),
                                    connectorConfig.maxRetries(),
                                    connectorConfig.retryDelay());
                    try {
                        // create the slot if it doesn't exist, otherwise update slot to add new
                        // table(job restore and add table)
                        replicationConnection.createReplicationSlot().orElse(null);
                    } catch (SQLException ex) {
                        String message = "Creation of replication slot failed";
                        if (ex.getMessage().contains("already exists")) {
                            message +=
                                    "; when setting up multiple connectors for the same database host, please make sure to use a distinct replication slot name for each.";
                            log.warn(message);
                        } else {
                            throw new DebeziumException(message, ex);
                        }
                    }
                }
            }

            try {
                dataConnection.commit();
            } catch (SQLException e) {
                throw new DebeziumException(e);
            }

            this.queue =
                    new ChangeEventQueue.Builder<DataChangeEvent>()
                            .pollInterval(connectorConfig.getPollInterval())
                            .maxBatchSize(connectorConfig.getMaxBatchSize())
                            .maxQueueSize(queueSize)
                            .maxQueueSizeInBytes(connectorConfig.getMaxQueueSizeInBytes())
                            .loggingContextSupplier(
                                    () -> taskContext.configureLoggingContext(CONTEXT_NAME))
                            // do not buffer any element, we use signal event
                            // .buffering()
                            .build();

            this.dispatcher =
                    new JdbcSourceEventDispatcher<>(
                            connectorConfig,
                            topicSelector,
                            databaseSchema,
                            queue,
                            connectorConfig.getTableFilters().dataCollectionFilter(),
                            DataChangeEvent::new,
                            metadataProvider,
                            schemaNameAdjuster);

            this.highGoEventDispatcher =
                    new HighGoEventDispatcher<>(
                            connectorConfig,
                            topicSelector,
                            databaseSchema,
                            queue,
                            connectorConfig.getTableFilters().dataCollectionFilter(),
                            DataChangeEvent::new,
                            metadataProvider,
                            schemaNameAdjuster);

            this.snapshotChangeEventSourceMetrics =
                    new DefaultChangeEventSourceMetricsFactory<HighGoPartition>()
                            .getSnapshotMetrics(taskContext, queue, metadataProvider);

            this.errorHandler = new HighGoErrorHandler(connectorConfig, queue);
        } finally {
            previousContext.restore();
        }
    }

    @Override
    public HighGoSourceConfig getSourceConfig() {
        return (HighGoSourceConfig) sourceConfig;
    }

    public HighGoConnection getDataConnection() {
        return dataConnection;
    }

    public SnapshotChangeEventSourceMetrics<HighGoPartition> getSnapshotChangeEventSourceMetrics() {
        return snapshotChangeEventSourceMetrics;
    }

    @Override
    public HighGoConnectorConfig getDbzConnectorConfig() {
        return (HighGoConnectorConfig) super.getDbzConnectorConfig();
    }

    @Override
    public HighGoOffsetContext getOffsetContext() {
        return offsetContext;
    }

    @Override
    public HighGoPartition getPartition() {
        return partition;
    }

    @Override
    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    @Override
    public HighGoSchema getDatabaseSchema() {
        return databaseSchema;
    }

    @Override
    public SeaTunnelRowType getSplitType(Table table) {
        return HighGoUtils.getSplitType(table);
    }

    @Override
    public JdbcSourceEventDispatcher<HighGoPartition> getDispatcher() {
        return dispatcher;
    }

    public HighGoEventDispatcher<TableId> getHighGoEventDispatcher() {
        return highGoEventDispatcher;
    }

    @Override
    public ChangeEventQueue<DataChangeEvent> getQueue() {
        return queue;
    }

    @Override
    public Tables.TableFilter getTableFilter() {
        return getDbzConnectorConfig().getTableFilters().dataCollectionFilter();
    }

    @Override
    public Offset getStreamOffset(SourceRecord sourceRecord) {
        return HighGoUtils.getLsnPosition(sourceRecord);
    }

    @Override
    public void close() {
        try {
            if (Objects.nonNull(dataConnection)) {
                this.dataConnection.close();
            }
            if (Objects.nonNull(replicationConnection)) {
                this.replicationConnection.close();
            }
        } catch (Exception e) {
            log.warn("Failed to close connection", e);
        }
    }

    /** Loads the connector's persistent offset (if present) via the given loader. */
    private HighGoOffsetContext loadStartingOffsetState(
            HighGoOffsetContext.Loader loader, SourceSplitBase split) {
        Offset offset =
                split.isSnapshotSplit()
                        ? LsnOffset.INITIAL_OFFSET
                        : split.asIncrementalSplit().getStartupOffset();
        return loader.load(offset.getOffset());
    }

    public ReplicationConnection createReplicationConnection(
            HighGoTaskContext taskContext, boolean doSnapshot, int maxRetries, Duration retryDelay)
            throws ConnectException {
        final Metronome metronome = Metronome.parker(retryDelay, Clock.SYSTEM);
        short retryCount = 0;
        ReplicationConnection replicationConnection = null;
        while (retryCount <= maxRetries) {
            try {
                return taskContext.createReplicationConnection(doSnapshot);
            } catch (SQLException ex) {
                retryCount++;
                if (retryCount > maxRetries) {
                    log.error(
                            "Too many errors connecting to server. All {} retries failed.",
                            maxRetries);
                    throw new ConnectException(ex);
                }

                log.warn(
                        "Error connecting to server; will attempt retry {} of {} after {} "
                                + "seconds. Exception message: {}",
                        retryCount,
                        maxRetries,
                        retryDelay.getSeconds(),
                        ex.getMessage());
                try {
                    metronome.pause();
                } catch (InterruptedException e) {
                    log.warn("Connection retry sleep interrupted by exception: " + e);
                    Thread.currentThread().interrupt();
                }
            }
        }
        return replicationConnection;
    }
}

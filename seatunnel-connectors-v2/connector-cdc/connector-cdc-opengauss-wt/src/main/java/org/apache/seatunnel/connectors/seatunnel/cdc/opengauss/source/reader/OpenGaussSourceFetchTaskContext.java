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

package org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.source.reader;

import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.cdc.base.config.JdbcSourceConfig;
import org.apache.seatunnel.connectors.cdc.base.dialect.JdbcDataSourceDialect;
import org.apache.seatunnel.connectors.cdc.base.relational.JdbcSourceEventDispatcher;
import org.apache.seatunnel.connectors.cdc.base.source.offset.Offset;
import org.apache.seatunnel.connectors.cdc.base.source.reader.external.JdbcSourceFetchTaskContext;
import org.apache.seatunnel.connectors.cdc.base.source.split.SourceSplitBase;
import org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.config.OpenGaussSourceConfig;
import org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.source.offset.LsnOffset;
import org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.utils.OpenGaussUtils;

import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;

import io.debezium.DebeziumException;
import io.debezium.connector.base.ChangeEventQueue;
import io.debezium.connector.opengauss.OpengaussConnectorConfig;
import io.debezium.connector.opengauss.OpengaussErrorHandler;
import io.debezium.connector.opengauss.OpengaussEventDispatcher;
import io.debezium.connector.opengauss.OpengaussEventMetadataProvider;
import io.debezium.connector.opengauss.OpengaussOffsetContext;
import io.debezium.connector.opengauss.OpengaussSchema;
import io.debezium.connector.opengauss.OpengaussTaskContext;
import io.debezium.connector.opengauss.OpengaussTopicSelector;
import io.debezium.connector.opengauss.OpengaussValueConverter;
import io.debezium.connector.opengauss.TypeRegistry;
import io.debezium.connector.opengauss.connection.OpengaussConnection;
import io.debezium.connector.opengauss.connection.ReplicationConnection;
import io.debezium.connector.opengauss.spi.SlotCreationResult;
import io.debezium.connector.opengauss.spi.SlotState;
import io.debezium.connector.opengauss.spi.Snapshotter;
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

@Slf4j
public class OpenGaussSourceFetchTaskContext extends JdbcSourceFetchTaskContext {

    private static final String CONTEXT_NAME = "opengauss-cdc-connector-task";

    private final OpengaussConnection dataConnection;

    @Getter private ReplicationConnection replicationConnection;

    private final OpengaussEventMetadataProvider metadataProvider;

    @Getter private Snapshotter snapshotter;
    private OpengaussSchema databaseSchema;
    private OpengaussOffsetContext offsetContext;
    private TopicSelector<TableId> topicSelector;
    private JdbcSourceEventDispatcher dispatcher;
    private OpengaussEventDispatcher opengaussEventDispatcher;
    private ChangeEventQueue<DataChangeEvent> queue;
    private OpengaussErrorHandler errorHandler;

    @Getter private OpengaussTaskContext taskContext;

    private SnapshotChangeEventSourceMetrics snapshotChangeEventSourceMetrics;

    private Collection<TableChanges.TableChange> engineHistory;

    public OpenGaussSourceFetchTaskContext(
            JdbcSourceConfig sourceConfig,
            JdbcDataSourceDialect dataSourceDialect,
            OpengaussConnection dataConnection,
            Collection<TableChanges.TableChange> engineHistory) {
        super(sourceConfig, dataSourceDialect);
        this.dataConnection = dataConnection;
        this.metadataProvider = new OpengaussEventMetadataProvider();
        this.engineHistory = engineHistory;
    }

    @Override
    public void configure(SourceSplitBase sourceSplitBase) {
        super.registerDatabaseHistory(sourceSplitBase, dataConnection);

        // initial stateful objects
        final OpengaussConnectorConfig connectorConfig = getDbzConnectorConfig();
        this.snapshotter = connectorConfig.getSnapshotter();

        this.topicSelector = OpengaussTopicSelector.create(connectorConfig);

        final OpengaussConnection.OpenGaussValueConverterBuilder valueConverterBuilder =
                typeRegistry ->
                        OpengaussValueConverter.of(
                                connectorConfig, dataConnection.getDatabaseCharset(), typeRegistry);
        final TypeRegistry typeRegistry = dataConnection.getTypeRegistry();

        this.databaseSchema =
                new OpengaussSchema(
                        connectorConfig,
                        typeRegistry,
                        topicSelector,
                        valueConverterBuilder.build(typeRegistry));
        this.taskContext = new OpengaussTaskContext(connectorConfig, databaseSchema, topicSelector);
        try {
            taskContext.refreshSchema(dataConnection, false);
        } catch (SQLException e) {
            throw new DebeziumException("load schema failed", e);
        }
        this.offsetContext =
                loadStartingOffsetState(
                        new OpengaussOffsetContext.Loader(connectorConfig), sourceSplitBase);

        final int queueSize =
                sourceSplitBase.isSnapshotSplit()
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

            SlotCreationResult slotCreatedInfo = null;
            if (snapshotter.shouldStream()) {
                final boolean doSnapshot = snapshotter.shouldSnapshot();
                this.replicationConnection =
                        createReplicationConnection(
                                this.taskContext,
                                doSnapshot,
                                connectorConfig.maxRetries(),
                                connectorConfig.retryDelay());

                // we need to create the slot before we start streaming if it doesn't exist
                // otherwise we can't stream back changes happening while the snapshot is taking
                // place
                if (slotInfo == null) {
                    try {
                        slotCreatedInfo =
                                replicationConnection.createReplicationSlot().orElse(null);
                    } catch (SQLException ex) {
                        String message = "Creation of replication slot failed";
                        if (ex.getMessage().contains("already exists")) {
                            message +=
                                    "; when setting up multiple connectors for the same database host, please make sure to use a distinct replication slot name for each.";
                        }
                        throw new DebeziumException(message, ex);
                    }
                } else {
                    slotCreatedInfo = null;
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
                    new JdbcSourceEventDispatcher(
                            connectorConfig,
                            topicSelector,
                            databaseSchema,
                            queue,
                            connectorConfig.getTableFilters().dataCollectionFilter(),
                            DataChangeEvent::new,
                            metadataProvider,
                            schemaNameAdjuster);

            this.opengaussEventDispatcher =
                    new OpengaussEventDispatcher<>(
                            connectorConfig,
                            topicSelector,
                            databaseSchema,
                            queue,
                            connectorConfig.getTableFilters().dataCollectionFilter(),
                            DataChangeEvent::new,
                            metadataProvider,
                            schemaNameAdjuster);

            this.snapshotChangeEventSourceMetrics =
                    new DefaultChangeEventSourceMetricsFactory()
                            .getSnapshotMetrics(taskContext, queue, metadataProvider);

            this.errorHandler = new OpengaussErrorHandler(connectorConfig.getLogicalName(), queue);
        } finally {
            previousContext.restore();
        }
    }

    @Override
    public OpenGaussSourceConfig getSourceConfig() {
        return (OpenGaussSourceConfig) sourceConfig;
    }

    public OpengaussConnection getDataConnection() {
        return dataConnection;
    }

    public SnapshotChangeEventSourceMetrics getSnapshotChangeEventSourceMetrics() {
        return snapshotChangeEventSourceMetrics;
    }

    @Override
    public OpengaussConnectorConfig getDbzConnectorConfig() {
        return (OpengaussConnectorConfig) super.getDbzConnectorConfig();
    }

    @Override
    public OpengaussOffsetContext getOffsetContext() {
        return offsetContext;
    }

    @Override
    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    @Override
    public OpengaussSchema getDatabaseSchema() {
        return databaseSchema;
    }

    @Override
    public SeaTunnelRowType getSplitType(Table table) {
        return OpenGaussUtils.getSplitType(table);
    }

    @Override
    public JdbcSourceEventDispatcher getDispatcher() {
        return dispatcher;
    }

    public OpengaussEventDispatcher<TableId> getOpenGaussEventDispatcher() {
        return opengaussEventDispatcher;
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
        return OpenGaussUtils.getLsnPosition(sourceRecord);
    }

    @Override
    public void close() {
        try {
            this.dataConnection.close();
            this.replicationConnection.close();
        } catch (Exception e) {
            log.warn("Failed to close connection", e);
        }
    }

    /** Loads the connector's persistent offset (if present) via the given loader. */
    private OpengaussOffsetContext loadStartingOffsetState(
            OpengaussOffsetContext.Loader loader, SourceSplitBase split) {
        Offset offset =
                split.isSnapshotSplit()
                        ? LsnOffset.INITIAL_OFFSET
                        : split.asIncrementalSplit().getStartupOffset();
        return loader.load(offset.getOffset());
    }

    public ReplicationConnection createReplicationConnection(
            OpengaussTaskContext taskContext,
            boolean doSnapshot,
            int maxRetries,
            Duration retryDelay)
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

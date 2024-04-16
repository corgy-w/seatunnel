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

package org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.source;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.ConstraintKey;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.common.utils.SeaTunnelException;
import org.apache.seatunnel.connectors.cdc.base.config.JdbcSourceConfig;
import org.apache.seatunnel.connectors.cdc.base.dialect.JdbcDataSourceDialect;
import org.apache.seatunnel.connectors.cdc.base.relational.connection.JdbcConnectionPoolFactory;
import org.apache.seatunnel.connectors.cdc.base.source.enumerator.splitter.ChunkSplitter;
import org.apache.seatunnel.connectors.cdc.base.source.reader.external.FetchTask;
import org.apache.seatunnel.connectors.cdc.base.source.split.IncrementalSplit;
import org.apache.seatunnel.connectors.cdc.base.source.split.SnapshotSplit;
import org.apache.seatunnel.connectors.cdc.base.source.split.SourceSplitBase;
import org.apache.seatunnel.connectors.cdc.base.utils.CatalogTableUtils;
import org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.config.OpenGaussSourceConfig;
import org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.config.OpenGaussSourceConfigFactory;
import org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.source.enumerator.OpenGaussChunkSplitter;
import org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.source.reader.OpenGaussSourceFetchTaskContext;
import org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.source.reader.snapshot.OpenGaussSnapshotFetchTask;
import org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.source.reader.wal.OpenGaussWalFetchTask;
import org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.utils.OpenGaussSchema;
import org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.utils.TableDiscoveryUtils;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;

import io.debezium.connector.opengauss.OpengaussConnectorConfig;
import io.debezium.connector.opengauss.connection.OpengaussConnection;
import io.debezium.jdbc.JdbcConnection;
import io.debezium.relational.TableId;
import io.debezium.relational.history.TableChanges;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.seatunnel.connectors.seatunnel.cdc.opengauss.utils.OpenGaussConnectionUtils.newOpenGaussValueConverterBuilder;

public class OpenGaussDialect implements JdbcDataSourceDialect {

    private static final long serialVersionUID = 1L;
    private final OpenGaussSourceConfig sourceConfig;

    private transient OpenGaussSchema postgresSchema;
    private final Map<TableId, CatalogTable> tableMap;
    private OpenGaussWalFetchTask openGaussWalFetchTask;

    public OpenGaussDialect(
            OpenGaussSourceConfigFactory configFactory, List<CatalogTable> catalogTables) {
        this.sourceConfig = configFactory.create(0);
        this.tableMap = CatalogTableUtils.convertTables(catalogTables);
    }

    @Override
    public String getName() {
        return DatabaseIdentifier.OPENGAUSS;
    }

    @Override
    public boolean isDataCollectionIdCaseSensitive(JdbcSourceConfig sourceConfig) {
        // todo: need to check the case sensitive of the database
        return true;
    }

    @Override
    public JdbcConnection openJdbcConnection(JdbcSourceConfig sourceConfig) {

        OpengaussConnectorConfig opengaussConnectorConfig =
                (OpengaussConnectorConfig) sourceConfig.getDbzConnectorConfig();

        return new OpengaussConnection(
                opengaussConnectorConfig.getJdbcConfig(),
                newOpenGaussValueConverterBuilder(opengaussConnectorConfig));
    }

    @Override
    public ChunkSplitter createChunkSplitter(JdbcSourceConfig sourceConfig) {
        return new OpenGaussChunkSplitter(sourceConfig, this);
    }

    @Override
    public JdbcConnectionPoolFactory getPooledDataSourceFactory() {
        return new OpenGaussPooledDataSourceFactory();
    }

    @Override
    public List<TableId> discoverDataCollections(JdbcSourceConfig sourceConfig) {
        OpenGaussSourceConfig postgresSourceConfig = (OpenGaussSourceConfig) sourceConfig;
        try (JdbcConnection jdbcConnection = openJdbcConnection(sourceConfig)) {
            return TableDiscoveryUtils.listTables(
                    jdbcConnection, postgresSourceConfig.getTableFilters());
        } catch (SQLException e) {
            throw new SeaTunnelException("Error to discover tables: " + e.getMessage(), e);
        }
    }

    @Override
    public TableChanges.TableChange queryTableSchema(JdbcConnection jdbc, TableId tableId) {
        if (postgresSchema == null) {
            postgresSchema = new OpenGaussSchema(sourceConfig.getDbzConnectorConfig(), tableMap);
        }
        return postgresSchema.getTableSchema(jdbc, tableId);
    }

    @Override
    public OpenGaussSourceFetchTaskContext createFetchTaskContext(
            SourceSplitBase sourceSplitBase, JdbcSourceConfig taskSourceConfig) {

        OpengaussConnectorConfig opengaussConnectorConfig =
                (OpengaussConnectorConfig) taskSourceConfig.getDbzConnectorConfig();

        final OpengaussConnection jdbcConnection =
                new OpengaussConnection(
                        opengaussConnectorConfig.getJdbcConfig(),
                        newOpenGaussValueConverterBuilder(opengaussConnectorConfig));

        List<TableChanges.TableChange> tableChangeList = new ArrayList<>();
        // TODO: support save table schema
        if (sourceSplitBase instanceof SnapshotSplit) {
            SnapshotSplit snapshotSplit = (SnapshotSplit) sourceSplitBase;
            tableChangeList.add(queryTableSchema(jdbcConnection, snapshotSplit.getTableId()));
        } else {
            IncrementalSplit incrementalSplit = (IncrementalSplit) sourceSplitBase;
            for (TableId tableId : incrementalSplit.getTableIds()) {
                tableChangeList.add(queryTableSchema(jdbcConnection, tableId));
            }
        }

        return new OpenGaussSourceFetchTaskContext(
                taskSourceConfig, this, jdbcConnection, tableChangeList);
    }

    @Override
    public FetchTask<SourceSplitBase> createFetchTask(SourceSplitBase sourceSplitBase) {
        if (sourceSplitBase.isSnapshotSplit()) {
            return new OpenGaussSnapshotFetchTask(sourceSplitBase.asSnapshotSplit());
        } else {
            openGaussWalFetchTask = new OpenGaussWalFetchTask(sourceSplitBase.asIncrementalSplit());
            return openGaussWalFetchTask;
        }
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) throws Exception {
        if (openGaussWalFetchTask != null) {
            openGaussWalFetchTask.commitCurrentOffset();
        }
    }

    @Override
    public Optional<PrimaryKey> getPrimaryKey(JdbcConnection jdbcConnection, TableId tableId) {
        return Optional.ofNullable(tableMap.get(tableId).getTableSchema().getPrimaryKey());
    }

    @Override
    public List<ConstraintKey> getConstraintKeys(JdbcConnection jdbcConnection, TableId tableId) {
        return tableMap.get(tableId).getTableSchema().getConstraintKeys();
    }
}

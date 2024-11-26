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

package org.apache.seatunnel.connectors.cdc.informix.source;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.ConstraintKey;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.common.utils.SeaTunnelException;
import org.apache.seatunnel.connectors.cdc.base.config.JdbcSourceConfig;
import org.apache.seatunnel.connectors.cdc.base.dialect.JdbcDataSourceDialect;
import org.apache.seatunnel.connectors.cdc.base.source.enumerator.splitter.ChunkSplitter;
import org.apache.seatunnel.connectors.cdc.base.source.reader.external.FetchTask;
import org.apache.seatunnel.connectors.cdc.base.source.reader.external.JdbcSourceFetchTaskContext;
import org.apache.seatunnel.connectors.cdc.base.source.split.SourceSplitBase;
import org.apache.seatunnel.connectors.cdc.base.utils.CatalogTableUtils;
import org.apache.seatunnel.connectors.cdc.informix.config.InformixSourceConfig;
import org.apache.seatunnel.connectors.cdc.informix.config.InformixSourceConfigFactory;
import org.apache.seatunnel.connectors.cdc.informix.source.eumerator.InformixChunkSplitter;
import org.apache.seatunnel.connectors.cdc.informix.source.reader.fetch.InformixSourceFetchTaskContext;
import org.apache.seatunnel.connectors.cdc.informix.source.reader.fetch.cdc.InformixCDCLogFetchTask;
import org.apache.seatunnel.connectors.cdc.informix.source.reader.fetch.snapshot.InformixSnapshotFetchTask;
import org.apache.seatunnel.connectors.cdc.informix.utils.InformixSchema;

import io.debezium.connector.informix.InformixConnection;
import io.debezium.connector.informix.InformixDatabaseSchema;
import io.debezium.connector.informix.Lsn;
import io.debezium.jdbc.JdbcConnection;
import io.debezium.relational.TableId;
import io.debezium.relational.history.TableChanges;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class InformixDialect implements JdbcDataSourceDialect {
    private final InformixSourceConfig sourceConfig;
    private transient InformixSchema informixSchema;

    private transient volatile Lsn globalLsn;
    private final Map<TableId, CatalogTable> tableMap;

    public InformixDialect(
            InformixSourceConfigFactory configFactory, List<CatalogTable> catalogTables) {
        this(configFactory.create(0), catalogTables);
    }

    public InformixDialect(InformixSourceConfig sourceConfig, List<CatalogTable> catalogTables) {
        this.sourceConfig = sourceConfig;
        this.tableMap = CatalogTableUtils.convertTables(catalogTables);
    }

    @Override
    public String getName() {
        return "Informix";
    }

    @Override
    public InformixConnection openJdbcConnection(JdbcSourceConfig sourceConfig) {
        return new InformixConnection(sourceConfig);
    }

    @Override
    public boolean isDataCollectionIdCaseSensitive(JdbcSourceConfig sourceConfig) {
        try (InformixConnection jdbcConnection = openJdbcConnection(sourceConfig)) {
            return jdbcConnection.isCaseSensitive();
        } catch (SQLException e) {
            throw new SeaTunnelException(
                    "Error reading Dameng system config: " + e.getMessage(), e);
        }
    }

    @Override
    public ChunkSplitter createChunkSplitter(JdbcSourceConfig sourceConfig) {
        return new InformixChunkSplitter(sourceConfig, this);
    }

    @Override
    public List<TableId> discoverDataCollections(JdbcSourceConfig sourceConfig) {
        InformixSourceConfig informixSourceConfig = (InformixSourceConfig) sourceConfig;
        try (InformixConnection jdbcConnection = openJdbcConnection(sourceConfig)) {
            List<TableId> tables =
                    jdbcConnection.listTables(
                            informixSourceConfig.getTableFilters(), sourceConfig.getDatabaseList());
            this.checkAllTablesEnabledCapture(jdbcConnection, tables);
            return tables;
        } catch (SQLException e) {
            throw new SeaTunnelException("Error to discover tables: " + e.getMessage(), e);
        }
    }

    @Override
    public void checkAllTablesEnabledCapture(JdbcConnection jdbcConnection, List<TableId> tableIds)
            throws SQLException {
        InformixConnection informixConnection = (InformixConnection) jdbcConnection;
        List<String> databases =
                tableIds.stream().map(TableId::catalog).distinct().collect(Collectors.toList());
        List<String> enableDatabases = informixConnection.listDatabasesWithEnableCDC(databases);
        Set<String> disableDatabases =
                databases.stream()
                        .filter(db -> !enableDatabases.contains(db))
                        .collect(Collectors.toSet());
        if (!disableDatabases.isEmpty()) {
            throw new SeaTunnelException(
                    "The following databases are not enabled for capture: " + disableDatabases);
        }
    }

    @Override
    public TableChanges.TableChange queryTableSchema(JdbcConnection jdbc, TableId tableId) {
        if (informixSchema == null) {
            synchronized (this) {
                if (informixSchema == null) {
                    informixSchema = new InformixSchema(tableMap);
                }
            }
        }
        return informixSchema.getTableSchema(jdbc, tableId);
    }

    @Override
    public JdbcSourceFetchTaskContext createFetchTaskContext(
            SourceSplitBase sourceSplitBase, JdbcSourceConfig taskSourceConfig) {

        return new InformixSourceFetchTaskContext(taskSourceConfig, this);
    }

    public Lsn getGlobalLsn(InformixConnection connection, InformixDatabaseSchema databaseSchema) {
        if (globalLsn == null) {
            globalLsn = connection.currentCheckpointLsn(databaseSchema);
        }
        return globalLsn;
    }

    @Override
    public FetchTask<SourceSplitBase> createFetchTask(SourceSplitBase sourceSplitBase) {
        if (sourceSplitBase.isSnapshotSplit()) {
            return new InformixSnapshotFetchTask(sourceSplitBase.asSnapshotSplit(), this);
        } else {
            try (JdbcConnection jdbcConnection = openJdbcConnection(sourceConfig)) {
                List<TableId> tables = sourceSplitBase.asIncrementalSplit().getTableIds();
                this.checkAllTablesEnabledCapture(jdbcConnection, tables);
            } catch (SQLException e) {
                throw new SeaTunnelException("Error to check tables: " + e.getMessage(), e);
            }
            return new InformixCDCLogFetchTask(sourceSplitBase.asIncrementalSplit());
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

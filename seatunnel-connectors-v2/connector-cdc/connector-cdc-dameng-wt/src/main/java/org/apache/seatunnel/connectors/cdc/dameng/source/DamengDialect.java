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

package org.apache.seatunnel.connectors.cdc.dameng.source;

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
import org.apache.seatunnel.connectors.cdc.dameng.config.DamengSourceConfig;
import org.apache.seatunnel.connectors.cdc.dameng.config.DamengSourceConfigFactory;
import org.apache.seatunnel.connectors.cdc.dameng.source.eumerator.DamengChunkSplitter;
import org.apache.seatunnel.connectors.cdc.dameng.source.reader.fetch.DamengSourceFetchTaskContext;
import org.apache.seatunnel.connectors.cdc.dameng.source.reader.fetch.logminer.DamengLogMinerFetchTask;
import org.apache.seatunnel.connectors.cdc.dameng.source.reader.fetch.snapshot.DamengSnapshotFetchTask;
import org.apache.seatunnel.connectors.cdc.dameng.utils.DamengConncetionUtils;
import org.apache.seatunnel.connectors.cdc.dameng.utils.DamengSchema;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;

import io.debezium.connector.dameng.DamengConnection;
import io.debezium.jdbc.JdbcConnection;
import io.debezium.relational.TableId;
import io.debezium.relational.history.TableChanges;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DamengDialect implements JdbcDataSourceDialect {
    private final DamengSourceConfig sourceConfig;
    private transient DamengSchema damengSchema;
    private final Map<TableId, CatalogTable> tableMap;

    public DamengDialect(
            DamengSourceConfigFactory configFactory, List<CatalogTable> catalogTables) {
        this(configFactory.create(0), catalogTables);
    }

    public DamengDialect(DamengSourceConfig sourceConfig, List<CatalogTable> catalogTables) {
        this.sourceConfig = sourceConfig;
        this.tableMap = CatalogTableUtils.convertTables(catalogTables);
    }

    @Override
    public String getName() {
        return DatabaseIdentifier.DAMENG;
    }

    @Override
    public DamengConnection openJdbcConnection(JdbcSourceConfig sourceConfig) {
        return DamengConncetionUtils.createDamengConnection(sourceConfig.getDbzConfiguration());
    }

    @Override
    public boolean isDataCollectionIdCaseSensitive(JdbcSourceConfig sourceConfig) {
        try (DamengConnection jdbcConnection = openJdbcConnection(sourceConfig)) {
            return jdbcConnection.isCaseSensitive();
        } catch (SQLException e) {
            throw new SeaTunnelException(
                    "Error reading Dameng system config: " + e.getMessage(), e);
        }
    }

    @Override
    public ChunkSplitter createChunkSplitter(JdbcSourceConfig sourceConfig) {
        return new DamengChunkSplitter(sourceConfig, this);
    }

    @Override
    public List<TableId> discoverDataCollections(JdbcSourceConfig sourceConfig) {
        DamengSourceConfig damengSourceConfig = (DamengSourceConfig) sourceConfig;
        String database = damengSourceConfig.getDbzConnectorConfig().getDatabaseName();
        try (DamengConnection jdbcConnection = openJdbcConnection(sourceConfig)) {
            return jdbcConnection.listTables(damengSourceConfig.getTableFilters(), database);
        } catch (SQLException e) {
            throw new SeaTunnelException("Error to discover tables: " + e.getMessage(), e);
        }
    }

    @Override
    public TableChanges.TableChange queryTableSchema(JdbcConnection jdbc, TableId tableId) {
        if (damengSchema == null) {
            synchronized (this) {
                if (damengSchema == null) {
                    damengSchema = new DamengSchema(sourceConfig.getDbzConnectorConfig(), tableMap);
                }
            }
        }
        return damengSchema.getTableSchema(jdbc, tableId);
    }

    @Override
    public JdbcSourceFetchTaskContext createFetchTaskContext(
            SourceSplitBase sourceSplitBase, JdbcSourceConfig taskSourceConfig) {

        return new DamengSourceFetchTaskContext(taskSourceConfig, this);
    }

    @Override
    public FetchTask<SourceSplitBase> createFetchTask(SourceSplitBase sourceSplitBase) {
        if (sourceSplitBase.isSnapshotSplit()) {
            return new DamengSnapshotFetchTask(sourceSplitBase.asSnapshotSplit());
        } else {
            return new DamengLogMinerFetchTask(sourceSplitBase.asIncrementalSplit());
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

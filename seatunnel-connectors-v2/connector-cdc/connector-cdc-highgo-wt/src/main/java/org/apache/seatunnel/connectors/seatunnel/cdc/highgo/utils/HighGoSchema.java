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

package org.apache.seatunnel.connectors.seatunnel.cdc.highgo.utils;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.common.utils.SeaTunnelException;
import org.apache.seatunnel.connectors.cdc.base.utils.CatalogTableUtils;

import io.debezium.connector.highgo.HighGoConnectorConfig;
import io.debezium.connector.highgo.connection.HighGoConnection;
import io.debezium.connector.highgo.connection.ServerInfo;
import io.debezium.jdbc.JdbcConnection;
import io.debezium.relational.Table;
import io.debezium.relational.TableId;
import io.debezium.relational.Tables;
import io.debezium.relational.history.TableChanges;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HighGoSchema {

    private final HighGoConnectorConfig connectorConfig;
    private final Map<TableId, TableChanges.TableChange> schemasByTableId;
    private final Map<TableId, CatalogTable> tableMap;

    public HighGoSchema(
            final HighGoConnectorConfig connectorConfig, Map<TableId, CatalogTable> tableMap) {
        this.schemasByTableId = new ConcurrentHashMap<>();
        this.connectorConfig = connectorConfig;
        this.tableMap = tableMap;
    }

    public TableChanges.TableChange getTableSchema(JdbcConnection jdbc, TableId tableId) {
        // read schema from cache first
        TableChanges.TableChange schema = schemasByTableId.get(tableId);
        if (schema == null) {
            schema = readTableSchema(jdbc, tableId);
        }
        return schema;
    }

    private TableChanges.TableChange readTableSchema(JdbcConnection jdbc, TableId tableId) {
        // Because the catalog is null in the postgresConnection.readSchema method
        TableId tableIdWithoutCatalog = new TableId(null, tableId.schema(), tableId.table());

        HighGoConnection postgresConnection = (HighGoConnection) jdbc;
        Tables tables = new Tables();
        try {
            ServerInfo.ReplicaIdentity replicaIdentity =
                    postgresConnection.readReplicaIdentityInfo(tableId);
            if (!ServerInfo.ReplicaIdentity.FULL.equals(replicaIdentity)) {
                throw new SeaTunnelException(
                        String.format(
                                "Table %s does not have a full replica identity, please execute: ALTER TABLE %s REPLICA IDENTITY FULL;",
                                tableId, tableId));
            }

            postgresConnection.readSchema(
                    tables,
                    tableIdWithoutCatalog.catalog(),
                    tableIdWithoutCatalog.schema(),
                    connectorConfig.getTableFilters().dataCollectionFilter(),
                    null,
                    false);
            for (TableId id : tables.tableIds()) {
                TableId idWithCatalog = new TableId(tableId.catalog(), id.schema(), id.table());
                if (tableMap.containsKey(idWithCatalog)) {
                    Table table =
                            CatalogTableUtils.mergeCatalogTableConfig(
                                    tables.forTable(id), tableMap.get(idWithCatalog));
                    TableChanges.TableChange tableChange =
                            new TableChanges.TableChange(
                                    TableChanges.TableChangeType.CREATE, table);
                    schemasByTableId.put(idWithCatalog, tableChange);
                }
            }
        } catch (SQLException e) {
            throw new SeaTunnelException(
                    String.format("Failed to read schema for table %s ", tableId), e);
        }

        if (!schemasByTableId.containsKey(tableId)) {
            throw new SeaTunnelException(
                    String.format("Can't obtain schema for table %s ", tableId));
        }

        return schemasByTableId.get(tableId);
    }
}

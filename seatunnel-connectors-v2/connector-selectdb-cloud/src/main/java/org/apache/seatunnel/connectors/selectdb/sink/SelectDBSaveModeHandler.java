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

package org.apache.seatunnel.connectors.selectdb.sink;

import org.apache.seatunnel.api.sink.DefaultSaveModeHandler;
import org.apache.seatunnel.api.table.catalog.Catalog;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.connectors.selectdb.catalog.SelectDBCatalog;
import org.apache.seatunnel.connectors.selectdb.config.SelectDBConfig;

public class SelectDBSaveModeHandler extends DefaultSaveModeHandler {

    private final SelectDBConfig selectDBConfig;

    SelectDBSaveModeHandler(
            SelectDBConfig selectDBConfig, Catalog catalog, CatalogTable catalogTable) {
        super(
                selectDBConfig.getSchemaSaveMode(),
                selectDBConfig.getDataSaveMode(),
                catalog,
                catalogTable,
                selectDBConfig.getCustomSql());
        this.selectDBConfig = selectDBConfig;
    }

    protected void createTable() {
        autoCreateTable(selectDBConfig.getSaveModeCreateTemplate());
    }

    private void autoCreateTable(String template) {
        String database = tablePath.getDatabaseName();
        String tableName = tablePath.getTableName();
        SelectDBCatalog catalog = (SelectDBCatalog) this.catalog;
        if (!catalog.databaseExists(database)) {
            catalog.createDatabase(TablePath.of(database, ""), true);
        }
        if (!catalog.tableExists(TablePath.of(database, tableName))) {
            catalog.createTable(
                    SelectDBSaveModeUtil.fillingCreateSql(
                            template, database, tableName, catalogTable.getTableSchema()));
        }
    }
}

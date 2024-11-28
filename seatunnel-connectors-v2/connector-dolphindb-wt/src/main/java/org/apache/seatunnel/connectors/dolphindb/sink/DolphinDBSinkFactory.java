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

package org.apache.seatunnel.connectors.dolphindb.sink;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.configuration.util.OptionRule;
import org.apache.seatunnel.api.sink.DataSaveMode;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.connector.TableSink;
import org.apache.seatunnel.api.table.factory.Factory;
import org.apache.seatunnel.api.table.factory.TableSinkFactory;
import org.apache.seatunnel.api.table.factory.TableSinkFactoryContext;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig;

import org.apache.commons.lang3.StringUtils;

import com.google.auto.service.AutoService;
import com.xxdb.multithreadedtablewriter.MultithreadedTableWriter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.seatunnel.api.table.catalog.schema.TableSchemaOptions.SCHEMA;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.ADDRESS;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.BATCH_SIZE;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.COMPRESS_TYPE;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.CUSTOM_SQL;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.DATABASE;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.DATA_SAVE_MODE;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.KEY_COL_NAMES;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.PARTITION_COLUMN;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.PASSWORD;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.SAVE_MODE_CREATE_TEMPLATE;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.SCHEMA_SAVE_MODE;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.TABLE;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.THROTTLE;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.USER;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.USE_SSL;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.WRITE_MODE;

@AutoService(Factory.class)
public class DolphinDBSinkFactory implements TableSinkFactory<SeaTunnelRow, Void, Void, Void> {

    @Override
    public String factoryIdentifier() {
        return DolphinDBConfig.PLUGIN_NAME;
    }

    @Override
    public OptionRule optionRule() {
        return OptionRule.builder()
                .required(ADDRESS, USER, PASSWORD, DATABASE)
                .optional(
                        USE_SSL,
                        SCHEMA,
                        BATCH_SIZE,
                        THROTTLE,
                        PARTITION_COLUMN,
                        WRITE_MODE,
                        COMPRESS_TYPE,
                        SCHEMA_SAVE_MODE,
                        DATA_SAVE_MODE,
                        SAVE_MODE_CREATE_TEMPLATE)
                .conditional(WRITE_MODE, MultithreadedTableWriter.Mode.M_Upsert, KEY_COL_NAMES)
                .conditional(DATA_SAVE_MODE, DataSaveMode.CUSTOM_PROCESSING, CUSTOM_SQL)
                .build();
    }

    @Override
    public List<String> excludeTablePlaceholderReplaceKeys() {
        return Arrays.asList(SAVE_MODE_CREATE_TEMPLATE.key());
    }

    @Override
    public TableSink<SeaTunnelRow, Void, Void, Void> createSink(TableSinkFactoryContext context) {
        ReadonlyConfig readonlyConfig = context.getOptions();
        CatalogTable catalogTable = context.getCatalogTable();
        if (!readonlyConfig.getOptional(TABLE).isPresent()) {
            catalogTable = tableNameFromUpstream(context);
            Map<String, String> map = readonlyConfig.toMap();
            if (StringUtils.isNotBlank(catalogTable.getTableId().getSchemaName())) {
                map.put(
                        TABLE.key(),
                        catalogTable.getTableId().getSchemaName()
                                + "_"
                                + catalogTable.getTableId().getTableName());
            } else {
                map.put(TABLE.key(), catalogTable.getTableId().getTableName());
            }
            readonlyConfig = ReadonlyConfig.fromMap(new HashMap<>(map));
        }

        String tableName = readonlyConfig.get(TABLE);
        catalogTable =
                CatalogTable.of(
                        TableIdentifier.of(
                                catalogTable.getCatalogName(),
                                readonlyConfig.get(DATABASE),
                                catalogTable.getTableId().getSchemaName(),
                                tableName),
                        catalogTable);

        final CatalogTable finalCatalogTable = catalogTable;
        final ReadonlyConfig finalReadonlyConfig = readonlyConfig;

        return () -> new DolphinDBSink(finalCatalogTable, finalReadonlyConfig);
    }

    private CatalogTable tableNameFromUpstream(TableSinkFactoryContext context) {
        ReadonlyConfig readonlyConfig = context.getOptions();
        CatalogTable catalogTable = context.getCatalogTable();
        TableIdentifier tableId = catalogTable.getTableId();
        TableIdentifier newTableId =
                TableIdentifier.of(
                        tableId.getCatalogName(),
                        readonlyConfig.get(DATABASE),
                        tableId.getSchemaName(),
                        tableId.getTableName());
        return CatalogTable.of(newTableId, catalogTable);
    }
}

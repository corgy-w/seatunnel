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

package org.apache.seatunnel.connectors.seatunnel.starrocks.sink;

import org.apache.seatunnel.api.common.CommonOptions;
import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.sink.DataSaveMode;
import org.apache.seatunnel.api.sink.DefaultSaveModeHandler;
import org.apache.seatunnel.api.sink.SaveModeHandler;
import org.apache.seatunnel.api.sink.SchemaSaveMode;
import org.apache.seatunnel.api.sink.SinkWriter;
import org.apache.seatunnel.api.sink.SupportSaveMode;
import org.apache.seatunnel.api.table.catalog.Catalog;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.factory.CatalogFactory;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.common.sink.AbstractSimpleSink;
import org.apache.seatunnel.connectors.seatunnel.common.sink.AbstractSinkWriter;
import org.apache.seatunnel.connectors.seatunnel.starrocks.catalog.StarRocksCatalogFactory;
import org.apache.seatunnel.connectors.seatunnel.starrocks.config.SinkConfig;
import org.apache.seatunnel.connectors.seatunnel.starrocks.config.StarRocksSinkOptions;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.apache.seatunnel.api.table.factory.FactoryUtil.discoverFactory;

public class StarRocksSink extends AbstractSimpleSink<SeaTunnelRow, Void>
        implements SupportSaveMode {

    private SeaTunnelRowType seaTunnelRowType;
    private final SinkConfig sinkConfig;
    private final DataSaveMode dataSaveMode;
    private final SchemaSaveMode schemaSaveMode;
    private final ReadonlyConfig readonlyConfig;
    private final CatalogTable catalogTable;

    public StarRocksSink(
            SinkConfig sinkConfig,
            CatalogTable catalogTable,
            final ReadonlyConfig readonlyConfig,
            SchemaSaveMode schemaSaveMode,
            DataSaveMode dataSaveMode) {
        this.sinkConfig = sinkConfig;
        this.seaTunnelRowType = catalogTable.getTableSchema().toPhysicalRowDataType();
        this.catalogTable = catalogTable;
        this.dataSaveMode = sinkConfig.getDataSaveMode();
        this.schemaSaveMode = schemaSaveMode;
        this.readonlyConfig = readonlyConfig;
    }

    @Override
    public String getPluginName() {
        return StarRocksCatalogFactory.IDENTIFIER;
    }

    @Override
    public AbstractSinkWriter<SeaTunnelRow, Void> createWriter(SinkWriter.Context context) {
        return new StarRocksSinkWriter(sinkConfig, seaTunnelRowType);
    }

    @Override
    public Optional<SaveModeHandler> getSaveModeHandler() {
        if (catalogTable == null) {
            return Optional.empty();
        }
        Map<String, String> catalogOptions = readonlyConfig.toMap();
        if (catalogOptions == null) {
            return Optional.empty();
        }
        String factoryId = readonlyConfig.get(CommonOptions.PLUGIN_NAME);
        if (StringUtils.isBlank(sinkConfig.getDatabase())) {
            return Optional.empty();
        }
        CatalogFactory catalogFactory =
                discoverFactory(
                        Thread.currentThread().getContextClassLoader(),
                        CatalogFactory.class,
                        factoryId);
        if (catalogFactory == null) {
            return Optional.empty();
        }
        TablePath tablePath =
                TablePath.of(
                        catalogTable.getTableId().getDatabaseName(),
                        catalogTable.getTableId().getSchemaName(),
                        catalogTable.getTableId().getTableName());
        Catalog catalog =
                catalogFactory.createCatalog(
                        catalogFactory.factoryIdentifier(),
                        ReadonlyConfig.fromMap(new HashMap<>(catalogOptions)));
        catalog.open();
        return Optional.of(
                new DefaultSaveModeHandler(
                        schemaSaveMode,
                        dataSaveMode,
                        catalog,
                        tablePath,
                        catalogTable,
                        readonlyConfig.get(StarRocksSinkOptions.CUSTOM_SQL)));
    }
}

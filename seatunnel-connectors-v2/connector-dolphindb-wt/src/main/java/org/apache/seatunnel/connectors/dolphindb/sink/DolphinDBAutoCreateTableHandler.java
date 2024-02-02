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

import org.apache.seatunnel.api.common.CommonOptions;
import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.Catalog;
import org.apache.seatunnel.api.table.catalog.CatalogOptions;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.factory.CatalogFactory;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import static org.apache.seatunnel.api.table.factory.FactoryUtil.discoverFactory;

@Slf4j
public class DolphinDBAutoCreateTableHandler {

    private final CatalogTable catalogTable;
    private final ReadonlyConfig readonlyConfig;
    private final SeaTunnelRowType seaTunnelRowType;

    public DolphinDBAutoCreateTableHandler(
            CatalogTable catalogTable,
            ReadonlyConfig readonlyConfig,
            SeaTunnelRowType seaTunnelRowType) {
        this.catalogTable = catalogTable;
        this.readonlyConfig = readonlyConfig;
        this.seaTunnelRowType = seaTunnelRowType;
    }

    public void createTable() {
        if (catalogTable == null) {
            log.warn("The catalogTable is null, skip create table");
            return;
        }
        Map<String, String> catalogOptions = readonlyConfig.get(CatalogOptions.CATALOG_OPTIONS);
        if (catalogOptions == null) {
            log.info("The catalogOptions is null, skip create table");
            return;
        }
        TablePath tablePath =
                TablePath.of(
                        readonlyConfig.get(DolphinDBConfig.DATABASE),
                        null,
                        readonlyConfig.get(DolphinDBConfig.TABLE));
        try (Catalog catalog = createCatalog()) {
            if (catalog == null) {
                return;
            }
            catalog.open();
            // table is exist？
            if (!catalog.tableExists(tablePath)) {
                catalog.createTable(tablePath, catalogTable, true);
            } else {
                log.warn("The table {} is exist, skip create table", tablePath);
            }
        }
    }

    private Catalog createCatalog() {
        Map<String, String> catalogOptions = readonlyConfig.get(CatalogOptions.CATALOG_OPTIONS);
        String factoryId = catalogOptions.get(CommonOptions.PLUGIN_NAME.key());
        CatalogFactory catalogFactory =
                discoverFactory(
                        Thread.currentThread().getContextClassLoader(),
                        CatalogFactory.class,
                        factoryId);
        if (catalogFactory == null) {
            return null;
        }
        // get catalog instance to operation database
        Catalog catalog =
                catalogFactory.createCatalog(
                        catalogFactory.factoryIdentifier(),
                        ReadonlyConfig.fromMap(new HashMap<>(catalogOptions)));
        return catalog;
    }
}

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
import org.apache.seatunnel.api.sink.SaveModeHandler;
import org.apache.seatunnel.api.sink.SinkWriter;
import org.apache.seatunnel.api.sink.SupportSaveMode;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig;
import org.apache.seatunnel.connectors.dolphindb.sink.writter.DolphinDBSinkWriter;
import org.apache.seatunnel.connectors.seatunnel.common.sink.AbstractSimpleSink;
import org.apache.seatunnel.connectors.seatunnel.common.sink.AbstractSinkWriter;

import java.io.IOException;
import java.util.Optional;

public class DolphinDBSink extends AbstractSimpleSink<SeaTunnelRow, Void>
        implements SupportSaveMode {

    private final ReadonlyConfig readonlyConfig;

    private SeaTunnelRowType seaTunnelRowType;

    private final CatalogTable catalogTable;

    public DolphinDBSink(CatalogTable catalogTable, ReadonlyConfig readonlyConfig) {
        this.catalogTable = catalogTable;
        this.readonlyConfig = readonlyConfig;
        this.seaTunnelRowType = catalogTable.getTableSchema().toPhysicalRowDataType();
    }

    @Override
    public String getPluginName() {
        return DolphinDBConfig.PLUGIN_NAME;
    }

    @Override
    public AbstractSinkWriter<SeaTunnelRow, Void> createWriter(SinkWriter.Context context)
            throws IOException {
        try {
            return new DolphinDBSinkWriter(catalogTable, readonlyConfig);
        } catch (Exception ex) {
            throw new IOException("Create DolphinDBSinkWriter failed", ex);
        }
    }

    @Override
    public Optional<SaveModeHandler> getSaveModeHandler() {
        return Optional.of(
                new DolphinDBSaveModeHandler(
                        readonlyConfig.get(DolphinDBConfig.SCHEMA_SAVE_MODE),
                        readonlyConfig.get(DolphinDBConfig.DATA_SAVE_MODE),
                        catalogTable,
                        readonlyConfig));
    }
}

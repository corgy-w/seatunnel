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

package org.apache.seatunnel.connectors.dolphindb.sink.writter;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.connectors.seatunnel.common.sink.AbstractSinkWriter;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Optional;

@Slf4j
public class DolphinDBSinkWriter extends AbstractSinkWriter<SeaTunnelRow, Void> {

    private final ReadonlyConfig pluginConfig;

    private DolphinDBUpsertWriter dolphinDBUpsertWriter;
    private DolphinDbDeleteWriter dolphinDbDeleteWriter;

    public DolphinDBSinkWriter(CatalogTable catalogTable, ReadonlyConfig pluginConfig)
            throws Exception {
        this.pluginConfig = pluginConfig;
        this.dolphinDBUpsertWriter = new DolphinDBUpsertWriter(catalogTable, pluginConfig);
        this.dolphinDbDeleteWriter = new DolphinDbDeleteWriter(catalogTable, pluginConfig);
    }

    @Override
    public void write(SeaTunnelRow element) {
        RowKind rowKind = element.getRowKind();
        if (rowKind == RowKind.DELETE) {
            // delete the data
            dolphinDbDeleteWriter.write(element);
        } else {
            dolphinDBUpsertWriter.write(element);
        }
    }

    @SneakyThrows
    @Override
    public Optional<Void> prepareCommit() {
        dolphinDBUpsertWriter.prepareCommit();
        dolphinDbDeleteWriter.prepareCommit();
        return super.prepareCommit();
    }

    @Override
    public void close() throws IOException {
        try (DolphinDBUpsertWriter dolphinDBUpsertWriter1 = dolphinDBUpsertWriter;
                DolphinDbDeleteWriter dolphinDbDeleteWriter1 = dolphinDbDeleteWriter) {

        } catch (Exception ex) {
            throw new IOException("Failed to close DolphinDBSinkWriter", ex);
        }
    }
}

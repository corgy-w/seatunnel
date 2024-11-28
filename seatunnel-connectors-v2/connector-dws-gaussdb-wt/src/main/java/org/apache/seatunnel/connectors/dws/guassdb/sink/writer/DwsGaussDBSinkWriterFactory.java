/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.dws.guassdb.sink.writer;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.sink.SinkWriter;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.connectors.dws.guassdb.sink.commit.DwsGaussDBSinkCommitInfo;
import org.apache.seatunnel.connectors.dws.guassdb.sink.config.DwsGaussDBSinkOption;
import org.apache.seatunnel.connectors.dws.guassdb.sink.sql.DwsGaussSqlGenerator;
import org.apache.seatunnel.connectors.dws.guassdb.sink.state.DwsGaussDBSinkState;

import java.sql.SQLException;
import java.util.List;

import static org.apache.seatunnel.connectors.dws.guassdb.sink.config.DwsGaussDBSinkOption.WRITE_MODE;

public class DwsGaussDBSinkWriterFactory {

    public static DwsGaussDBSinkWriter createDwsGaussDBSinkWriter(
            DwsGaussSqlGenerator sqlGenerator,
            CatalogTable catalogTable,
            ReadonlyConfig readonlyConfig)
            throws SQLException {

        DwsGaussDBSinkOption.WriteMode writeMode = readonlyConfig.get(WRITE_MODE);
        switch (writeMode) {
            case APPEND_ONLY:
                return new DwsGaussDBAppendOnlySinkWriter(
                        sqlGenerator, catalogTable, readonlyConfig);
            case USING_TEMPORARY_TABLE:
                return new DwsGaussDBUsingTemporaryTableSinkWriter(
                        sqlGenerator, catalogTable, readonlyConfig, false);
            default:
                throw new IllegalArgumentException("Unsupported write mode: " + writeMode);
        }
    }

    public static SinkWriter<SeaTunnelRow, DwsGaussDBSinkCommitInfo, DwsGaussDBSinkState>
            createDwsGaussDBRestoreWriter(
                    DwsGaussSqlGenerator sqlGenerator,
                    CatalogTable catalogTable,
                    ReadonlyConfig readonlyConfig,
                    SinkWriter.Context context,
                    List<DwsGaussDBSinkState> states)
                    throws SQLException {

        DwsGaussDBSinkOption.WriteMode writeMode = readonlyConfig.get(WRITE_MODE);
        switch (writeMode) {
            case APPEND_ONLY:
                return new DwsGaussDBAppendOnlySinkWriter(
                        sqlGenerator, catalogTable, readonlyConfig);
            case USING_TEMPORARY_TABLE:
                return new DwsGaussDBUsingTemporaryTableSinkWriter(
                        sqlGenerator, catalogTable, readonlyConfig, true);
            default:
                throw new IllegalArgumentException("Unsupported write mode: " + writeMode);
        }
    }
}

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

package org.apache.seatunnel.connectors.seatunnel.pimetadata.source;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.source.Boundedness;
import org.apache.seatunnel.api.source.SeaTunnelSource;
import org.apache.seatunnel.api.source.SourceReader;
import org.apache.seatunnel.api.source.SourceSplitEnumerator;
import org.apache.seatunnel.api.source.SupportParallelism;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.pi.config.PIConfig;
import org.apache.seatunnel.connectors.seatunnel.pi.config.PIConfigHelper;
import org.apache.seatunnel.connectors.seatunnel.pi.utils.PISchemaBuilder;
import org.apache.seatunnel.connectors.seatunnel.pimetadata.split.PIMetadataSplit;
import org.apache.seatunnel.connectors.seatunnel.pimetadata.state.PIMetadataEnumeratorState;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class PIMetadataSource
        implements SeaTunnelSource<SeaTunnelRow, PIMetadataSplit, PIMetadataEnumeratorState>,
                SupportParallelism {

    public static final String CONNECTOR_IDENTITY = "PIMetadata";

    private final PIConfigHelper configHelper;
    private final SeaTunnelRowType rowType;
    private final CatalogTable catalogTable;

    public PIMetadataSource(ReadonlyConfig config) {
        // Parse connector-pi configuration
        this.configHelper = new PIConfigHelper(config);

        // Build connector-pi Schema
        if (config.getOptional(PIConfig.SCHEMA).isPresent()) {
            // User Schema
            this.rowType = PISchemaBuilder.createRowTypeFromUserSchema(config);

        } else {
            // Default Schema
            this.rowType = PISchemaBuilder.createDefaultRowType();
        }

        // Create CatalogTable
        this.catalogTable = createCatalogTable(config);
    }

    @Override
    public String getPluginName() {
        return CONNECTOR_IDENTITY;
    }

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.BOUNDED;
    }

    @Override
    public SeaTunnelRowType getProducedType() {
        return rowType;
    }

    @Override
    public List<CatalogTable> getProducedCatalogTables() {
        return Collections.singletonList(catalogTable);
    }

    @Override
    public SourceReader<SeaTunnelRow, PIMetadataSplit> createReader(
            SourceReader.Context readerContext) throws Exception {
        return new PIMetadataSourceReader(readerContext, configHelper, rowType);
    }

    @Override
    public SourceSplitEnumerator<PIMetadataSplit, PIMetadataEnumeratorState> createEnumerator(
            SourceSplitEnumerator.Context<PIMetadataSplit> enumeratorContext) throws Exception {
        return new PIMetadataSplitEnumerator(enumeratorContext, configHelper);
    }

    @Override
    public SourceSplitEnumerator<PIMetadataSplit, PIMetadataEnumeratorState> restoreEnumerator(
            SourceSplitEnumerator.Context<PIMetadataSplit> enumeratorContext,
            PIMetadataEnumeratorState checkpointState)
            throws Exception {
        return new PIMetadataSplitEnumerator(enumeratorContext, configHelper, checkpointState);
    }

    private CatalogTable createCatalogTable(ReadonlyConfig config) {
        TableSchema tableSchema;

        if (config.getOptional(PIConfig.SCHEMA).isPresent()) {
            Map<String, Object> schemaMap = config.get(PIConfig.SCHEMA);

            // Columns format, supports columnLength attribute
            if (schemaMap.containsKey("columns")) {
                tableSchema = PISchemaBuilder.createTableSchemaFromColumns(schemaMap);

            } else if (schemaMap.containsKey("fields")) {
                // Compatible: fields format
                tableSchema =
                        TableSchema.builder()
                                .columns(new ArrayList<>()) // Null, SeaTunnelRowType
                                .build();

            } else {
                throw new IllegalArgumentException(
                        "Schema Configuration missing columns or fields definition");
            }
        } else {
            // Default Schema
            tableSchema =
                    TableSchema.builder()
                            .columns(new ArrayList<>()) // Null, Default SeaTunnelRowType
                            .build();
        }

        return CatalogTable.of(
                TableIdentifier.of("pi_metadata", "default", "pi_metadata_table"),
                tableSchema,
                config.toMap(),
                new ArrayList<>(),
                "PI Metadata Data source table");
    }
}

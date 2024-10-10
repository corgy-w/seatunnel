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

package org.apache.seatunnel.transform.adaptsink;

import org.apache.seatunnel.shade.com.fasterxml.jackson.core.type.TypeReference;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;
import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.CatalogTableUtil;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
public class AdaptSinkTransformConfig {

    public static final Option<Boolean> ADAPT_SINK_TABLE_TYPE =
            Options.key("adapt_sink_table_type")
                    .booleanType()
                    .defaultValue(true)
                    .withDescription("Whether to adapt the sink table type");
    public static final Option<Boolean> ADAPT_SINK_TABLE_COLUMNS =
            Options.key("adapt_sink_table_columns")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Whether to adapt the sink table columns");
    public static final Option<List<Map<String, Object>>> SINK_TABLES =
            Options.key("sink_tables")
                    .type(new TypeReference<List<Map<String, Object>>>() {})
                    .noDefaultValue()
                    .withDescription("The multiple table schema");

    private boolean adaptSinkTableType;
    private boolean adaptSinkTableColumns;
    private Map<String, CatalogTable> sinkTables;

    public static AdaptSinkTransformConfig of(ReadonlyConfig config) {
        List<ReadonlyConfig> tableConfigs =
                config.get(SINK_TABLES).stream()
                        .map(ReadonlyConfig::fromMap)
                        .collect(Collectors.toList());
        Map<String, CatalogTable> tables =
                tableConfigs.stream()
                        .map(CatalogTableUtil::buildWithConfig)
                        .collect(Collectors.toMap(e -> e.getTablePath().toString(), e -> e));

        Boolean adaptSinkTableType = config.get(ADAPT_SINK_TABLE_TYPE);
        Boolean adaptSinkTableColumns = config.get(ADAPT_SINK_TABLE_COLUMNS);
        if (!adaptSinkTableType && !adaptSinkTableColumns) {
            throw new IllegalArgumentException(
                    "The adapt sink table type and adapt sink table columns must be set at least one");
        }

        return new AdaptSinkTransformConfig(adaptSinkTableType, adaptSinkTableColumns, tables);
    }
}

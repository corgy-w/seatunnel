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

package org.apache.seatunnel.connectors.seatunnel.cdc.oracle.source;

import org.apache.seatunnel.api.configuration.util.OptionRule;
import org.apache.seatunnel.api.source.SeaTunnelSource;
import org.apache.seatunnel.api.source.SourceSplit;
import org.apache.seatunnel.api.table.catalog.CatalogOptions;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.CatalogTableUtil;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.connector.TableSource;
import org.apache.seatunnel.api.table.factory.Factory;
import org.apache.seatunnel.api.table.factory.TableSourceFactory;
import org.apache.seatunnel.api.table.factory.TableSourceFactoryContext;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.connectors.cdc.base.option.JdbcSourceOptions;
import org.apache.seatunnel.connectors.cdc.base.option.SourceOptions;
import org.apache.seatunnel.connectors.cdc.base.option.StartupMode;
import org.apache.seatunnel.connectors.cdc.base.option.StopMode;
import org.apache.seatunnel.connectors.seatunnel.cdc.oracle.config.OracleSourceOptions;
import org.apache.seatunnel.connectors.seatunnel.cdc.oracle.config.OracleTableConfig;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.JdbcCatalogOptions;

import org.apache.commons.collections4.CollectionUtils;

import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@AutoService(Factory.class)
public class OracleIncrementalSourceFactory implements TableSourceFactory {
    @Override
    public String factoryIdentifier() {
        return OracleIncrementalSource.IDENTIFIER;
    }

    @Override
    public OptionRule optionRule() {
        return JdbcSourceOptions.getBaseRule()
                .required(
                        JdbcSourceOptions.USERNAME,
                        JdbcSourceOptions.PASSWORD,
                        CatalogOptions.TABLE_NAMES,
                        JdbcCatalogOptions.BASE_URL)
                .optional(
                        JdbcSourceOptions.PORT,
                        JdbcSourceOptions.DATABASE_NAMES,
                        JdbcSourceOptions.SERVER_TIME_ZONE,
                        JdbcSourceOptions.CONNECT_TIMEOUT_MS,
                        JdbcSourceOptions.CONNECT_MAX_RETRIES,
                        JdbcSourceOptions.CONNECTION_POOL_SIZE,
                        JdbcSourceOptions.CHUNK_KEY_EVEN_DISTRIBUTION_FACTOR_LOWER_BOUND,
                        JdbcSourceOptions.CHUNK_KEY_EVEN_DISTRIBUTION_FACTOR_UPPER_BOUND,
                        JdbcSourceOptions.SAMPLE_SHARDING_THRESHOLD)
                .optional(
                        OracleSourceOptions.STARTUP_MODE,
                        OracleSourceOptions.STOP_MODE,
                        OracleSourceOptions.TABLE_NAMES_CONFIG)
                .conditional(
                        OracleSourceOptions.STARTUP_MODE,
                        StartupMode.SPECIFIC,
                        SourceOptions.STARTUP_SPECIFIC_OFFSET_POS)
                .conditional(
                        OracleSourceOptions.STOP_MODE,
                        StopMode.SPECIFIC,
                        SourceOptions.STOP_SPECIFIC_OFFSET_POS)
                .conditional(
                        OracleSourceOptions.STARTUP_MODE,
                        StartupMode.TIMESTAMP,
                        SourceOptions.STARTUP_TIMESTAMP)
                .conditional(
                        OracleSourceOptions.STOP_MODE,
                        StopMode.TIMESTAMP,
                        SourceOptions.STOP_TIMESTAMP)
                .conditional(
                        OracleSourceOptions.STARTUP_MODE,
                        StartupMode.INITIAL,
                        SourceOptions.EXACTLY_ONCE)
                .build();
    }

    @Override
    public Class<? extends SeaTunnelSource> getSourceClass() {
        return OracleIncrementalSource.class;
    }

    @Override
    public <T, SplitT extends SourceSplit, StateT extends Serializable>
            TableSource<T, SplitT, StateT> createSource(TableSourceFactoryContext context) {
        return () -> {
            List<CatalogTable> catalogTables =
                    CatalogTableUtil.getCatalogTables(
                            context.getOptions(), context.getClassLoader());
            Optional<List<OracleTableConfig>> tableConfigs =
                    context.getOptions().getOptional(OracleSourceOptions.TABLE_NAMES_CONFIG);
            if (tableConfigs.isPresent()) {
                catalogTables = mergeCatalogTableConfig(catalogTables, tableConfigs.get());
            }

            SeaTunnelDataType<SeaTunnelRow> dataType =
                    CatalogTableUtil.convertToMultipleRowType(catalogTables);
            return new OracleIncrementalSource(context.getOptions(), dataType, catalogTables);
        };
    }

    private static List<CatalogTable> mergeCatalogTableConfig(
            List<CatalogTable> tables, List<OracleTableConfig> configs) {

        Map<TablePath, CatalogTable> catalogTableMap =
                tables.stream()
                        .collect(Collectors.toMap(t -> t.getTableId().toTablePath(), t -> t));
        for (OracleTableConfig catalogTableConfig : configs) {
            TablePath tablePath = TablePath.of(catalogTableConfig.getTable(), true);
            CatalogTable catalogTable = catalogTableMap.get(tablePath);
            if (CollectionUtils.isNotEmpty(catalogTableConfig.getPrimaryKeys())) {
                List<String> columnNames =
                        catalogTable.getTableSchema().getColumns().stream()
                                .map(c -> c.getName())
                                .collect(Collectors.toList());
                for (String pk : catalogTableConfig.getPrimaryKeys()) {
                    if (!columnNames.contains(pk)) {
                        throw new IllegalArgumentException(
                                String.format(
                                        "Primary key(%s) is not in table(%s) columns(%s)",
                                        pk, tablePath, columnNames));
                    }
                }
                catalogTable =
                        CatalogTable.of(
                                catalogTable.getTableId(),
                                TableSchema.builder()
                                        .columns(catalogTable.getTableSchema().getColumns())
                                        .constraintKey(
                                                catalogTable.getTableSchema().getConstraintKeys())
                                        .primaryKey(
                                                PrimaryKey.of(
                                                        "pk"
                                                                + Math.abs(
                                                                        catalogTableConfig
                                                                                .getPrimaryKeys()
                                                                                .hashCode()),
                                                        catalogTableConfig.getPrimaryKeys()))
                                        .build(),
                                catalogTable.getOptions(),
                                catalogTable.getPartitionKeys(),
                                catalogTable.getComment());
                log.info(
                        "Override primary key({}) for catalog table {}",
                        catalogTableConfig.getPrimaryKeys(),
                        tablePath);
            }
            catalogTableMap.put(tablePath, catalogTable);
        }
        return catalogTableMap.values().stream().collect(Collectors.toList());
    }
}

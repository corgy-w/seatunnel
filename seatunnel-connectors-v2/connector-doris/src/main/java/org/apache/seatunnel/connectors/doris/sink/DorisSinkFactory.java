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

package org.apache.seatunnel.connectors.doris.sink;

import org.apache.seatunnel.api.common.SeaTunnelAPIErrorCode;
import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.configuration.util.OptionRule;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.ConstraintKey;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.connector.TableSink;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.api.table.converter.TypeConverter;
import org.apache.seatunnel.api.table.factory.CatalogFactory;
import org.apache.seatunnel.api.table.factory.Factory;
import org.apache.seatunnel.api.table.factory.TableSinkFactory;
import org.apache.seatunnel.api.table.factory.TableSinkFactoryContext;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.common.constants.PluginType;
import org.apache.seatunnel.connectors.doris.catalog.DorisCatalog;
import org.apache.seatunnel.connectors.doris.config.DorisOptions;
import org.apache.seatunnel.connectors.doris.exception.DorisConnectorException;
import org.apache.seatunnel.connectors.doris.sink.committer.DorisCommitInfo;
import org.apache.seatunnel.connectors.doris.sink.writer.DorisSinkState;
import org.apache.seatunnel.connectors.doris.util.UnsupportedTypeConverterUtils;

import org.apache.commons.lang3.StringUtils;

import com.google.auto.service.AutoService;
import com.google.common.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.seatunnel.api.table.factory.FactoryUtil.discoverFactory;
import static org.apache.seatunnel.connectors.doris.config.DorisOptions.COLUMN_PATTERN;
import static org.apache.seatunnel.connectors.doris.config.DorisOptions.COLUMN_REPLACEMENT;
import static org.apache.seatunnel.connectors.doris.config.DorisOptions.DATABASE;
import static org.apache.seatunnel.connectors.doris.config.DorisOptions.NEEDS_UNSUPPORTED_TYPE_CASTING;
import static org.apache.seatunnel.connectors.doris.config.DorisOptions.TABLE;
import static org.apache.seatunnel.connectors.doris.config.DorisOptions.TABLE_IDENTIFIER;

@AutoService(Factory.class)
public class DorisSinkFactory implements TableSinkFactory {

    public static final String IDENTIFIER = "Doris";

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public OptionRule optionRule() {
        return DorisOptions.SINK_RULE.build();
    }

    @Override
    public List<String> excludeTablePlaceholderReplaceKeys() {
        return Arrays.asList(DorisOptions.SAVE_MODE_CREATE_TEMPLATE.key());
    }

    @Override
    public TableSink<SeaTunnelRow, DorisSinkState, DorisCommitInfo, DorisCommitInfo> createSink(
            TableSinkFactoryContext context) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        ReadonlyConfig config = context.getOptions();
        CatalogTable catalogTable =
                config.get(NEEDS_UNSUPPORTED_TYPE_CASTING)
                        ? UnsupportedTypeConverterUtils.convertCatalogTable(
                                context.getCatalogTable())
                        : context.getCatalogTable();
        final CatalogTable finalCatalogTable = this.renameCatalogTable(config, catalogTable);
        CatalogFactory catalogFactory =
                discoverFactory(
                        Thread.currentThread().getContextClassLoader(),
                        CatalogFactory.class,
                        "Doris");
        if (catalogFactory == null) {
            throw new DorisConnectorException(
                    SeaTunnelAPIErrorCode.CONFIG_VALIDATION_FAILED,
                    String.format(
                            "PluginName: %s, PluginType: %s, Message: %s",
                            factoryIdentifier(),
                            PluginType.SINK,
                            "Cannot find Doris catalog factory"));
        }
        DorisCatalog catalog =
                (DorisCatalog)
                        catalogFactory.createCatalog(catalogFactory.factoryIdentifier(), config);
        catalog.open();
        TypeConverter<BasicTypeDefine> typeConverter = catalog.getTypeConverter();
        catalog.close();
        return () -> new DorisSink(config, finalCatalogTable, typeConverter);
    }

    private CatalogTable renameCatalogTable(ReadonlyConfig options, CatalogTable catalogTable) {

        TableIdentifier tableId = catalogTable.getTableId();
        String tableName;
        String databaseName;
        String tableIdentifier = options.get(TABLE_IDENTIFIER);
        if (StringUtils.isNotEmpty(tableIdentifier)) {
            tableName = tableIdentifier.split("\\.")[1];
            databaseName = tableIdentifier.split("\\.")[0];
        } else {
            if (StringUtils.isNotEmpty(options.get(TABLE))) {
                tableName = options.get(TABLE);
            } else {
                tableName = tableId.getTableName();
            }

            if (StringUtils.isNotEmpty(options.get(DATABASE))) {
                databaseName = options.get(DATABASE);
            } else {
                databaseName = tableId.getDatabaseName();
            }
        }

        TableIdentifier newTableId =
                TableIdentifier.of(tableId.getCatalogName(), databaseName, null, tableName);

        if (options.get(COLUMN_PATTERN) != null && options.get(COLUMN_REPLACEMENT) != null) {
            catalogTable =
                    replaceColumnName(
                            catalogTable,
                            options.get(COLUMN_PATTERN),
                            options.get(COLUMN_REPLACEMENT));
        }

        return CatalogTable.of(newTableId, catalogTable);
    }

    @VisibleForTesting
    CatalogTable replaceColumnName(CatalogTable catalogTable, String original, String replacement) {
        checkNotNull(original, "original can not be null");
        checkNotNull(replacement, "replacement can not be null");
        checkArgument(StringUtils.isNotEmpty(original), "original can not be empty");
        List<Column> columns = catalogTable.getTableSchema().getColumns();
        Map<String, String> changedName = new LinkedHashMap<>();
        List<Column> newColumns =
                columns.stream()
                        .map(
                                column -> {
                                    if (column.getName().contains(original)) {
                                        changedName.put(
                                                column.getName(),
                                                column.getName().replace(original, replacement));
                                        return column.rename(changedName.get(column.getName()));
                                    }
                                    return column;
                                })
                        .collect(Collectors.toList());
        List<String> newPrimaryKey = null;
        if (catalogTable.getTableSchema().getPrimaryKey() != null
                && catalogTable.getTableSchema().getPrimaryKey().getColumnNames() != null) {
            newPrimaryKey =
                    catalogTable.getTableSchema().getPrimaryKey().getColumnNames().stream()
                            .map(key -> changedName.getOrDefault(key, key))
                            .collect(Collectors.toList());
        }
        List<ConstraintKey> newConstraintKeys =
                catalogTable.getTableSchema().getConstraintKeys().stream()
                        .map(
                                key -> {
                                    List<ConstraintKey.ConstraintKeyColumn> columnNames =
                                            key.getColumnNames().stream()
                                                    .map(
                                                            column ->
                                                                    ConstraintKey
                                                                            .ConstraintKeyColumn.of(
                                                                            changedName
                                                                                    .getOrDefault(
                                                                                            column
                                                                                                    .getColumnName(),
                                                                                            column
                                                                                                    .getColumnName()),
                                                                            column.getSortType()))
                                                    .collect(Collectors.toList());
                                    return ConstraintKey.of(
                                            key.getConstraintType(),
                                            key.getConstraintName(),
                                            columnNames);
                                })
                        .collect(Collectors.toList());
        TableSchema newTableSchema =
                TableSchema.builder()
                        .columns(newColumns)
                        .primaryKey(
                                newPrimaryKey == null
                                        ? null
                                        : PrimaryKey.of(
                                                catalogTable
                                                        .getTableSchema()
                                                        .getPrimaryKey()
                                                        .getPrimaryKey(),
                                                newPrimaryKey))
                        .constraintKey(newConstraintKeys)
                        .build();

        return CatalogTable.of(
                catalogTable.getTableId(),
                newTableSchema,
                catalogTable.getOptions(),
                catalogTable.getPartitionKeys(),
                catalogTable.getComment());
    }
}

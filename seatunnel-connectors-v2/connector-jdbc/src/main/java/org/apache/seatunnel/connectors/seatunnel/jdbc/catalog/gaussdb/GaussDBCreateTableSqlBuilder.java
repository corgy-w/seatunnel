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

package org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.gaussdb;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.ConstraintKey;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.psql.PostgresCreateTableSqlBuilder;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.utils.CatalogUtils;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.gaussdb.GaussDBTypeConverter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class GaussDBCreateTableSqlBuilder extends PostgresCreateTableSqlBuilder {
    private static final List<String> COMPATIBLE_DATABASES =
            Arrays.asList(
                    DatabaseIdentifier.GAUSSDB.toUpperCase(),
                    DatabaseIdentifier.POSTGRESQL.toUpperCase());

    private List<Column> columns;
    private PrimaryKey primaryKey;
    private String comment;
    private String sourceCatalogName;
    private String fieldIde;
    private List<ConstraintKey> constraintKeys;
    public Boolean isHaveConstraintKey = false;

    @Getter public List<String> createIndexSqls = new ArrayList<>();

    public GaussDBCreateTableSqlBuilder(CatalogTable catalogTable) {
        super(catalogTable);
        this.columns = catalogTable.getTableSchema().getColumns();
        this.primaryKey = catalogTable.getTableSchema().getPrimaryKey();
        this.comment = catalogTable.getComment();
        this.sourceCatalogName = catalogTable.getCatalogName();
        this.fieldIde = catalogTable.getOptions().get("fieldIde");
        this.constraintKeys = catalogTable.getTableSchema().getConstraintKeys();
    }

    public String build(TablePath tablePath) {
        StringBuilder createTableSql = new StringBuilder();
        createTableSql
                .append(CatalogUtils.quoteIdentifier("CREATE TABLE IF NOT EXISTS ", fieldIde))
                .append(tablePath.getSchemaAndTableName("\""))
                .append(" (\n");

        List<String> columnSqls =
                columns.stream()
                        .map(
                                column ->
                                        CatalogUtils.quoteIdentifier(
                                                buildColumnSql(column), fieldIde))
                        .collect(Collectors.toList());

        if (primaryKey != null) {
            columnSqls.add("\t" + buildPrimaryKeySql());
        }

        if (CollectionUtils.isNotEmpty(constraintKeys)) {
            for (ConstraintKey constraintKey : constraintKeys) {
                if (StringUtils.isBlank(constraintKey.getConstraintName())
                        || (primaryKey != null
                                && primaryKey
                                        .getColumnNames()
                                        .equals(constraintKey.getColumnNames()))) {
                    continue;
                }
                if (constraintKey.getConstraintType() == ConstraintKey.ConstraintType.UNIQUE_KEY) {
                    isHaveConstraintKey = true;
                    columnSqls.add("\t" + buildUniqueKeySql(constraintKey));
                } else if (constraintKey.getConstraintType()
                        == ConstraintKey.ConstraintType.INDEX_KEY) {
                    createIndexSqls.add(buildIndexKeySql(constraintKey, tablePath));
                }
            }
        }

        createTableSql.append(String.join(",\n", columnSqls));
        createTableSql.append("\n)");

        if (StringUtils.isNotBlank(comment)) {
            createTableSql.append(" COMMENT '").append(comment).append("'");
        }

        return createTableSql.toString();
    }

    private String buildColumnSql(Column column) {
        StringBuilder columnSql = new StringBuilder();
        columnSql.append("\t\"").append(column.getName()).append("\" ");

        // Convert SeaTunnel type to DWS type
        String dwsType = convertToDwsType(column);
        columnSql.append(dwsType);

        // Handle nullable
        if (!column.isNullable()) {
            columnSql.append(" NOT NULL");
        }

        // Handle default value
        if (column.getDefaultValue() != null) {
            columnSql.append(" DEFAULT ");
            if (column.getDefaultValue() instanceof String) {
                columnSql.append("'").append(column.getDefaultValue()).append("'");
            } else {
                columnSql.append(column.getDefaultValue());
            }
        }

        // Handle comment
        if (StringUtils.isNotBlank(column.getComment())) {
            columnSql.append(" COMMENT '").append(column.getComment()).append("'");
        }

        return columnSql.toString();
    }

    private String convertToDwsType(Column column) {
        // Use DwsTypeConverter to convert back to DWS type
        BasicTypeDefine typeDefine = GaussDBTypeConverter.INSTANCE.reconvert(column);
        if (typeDefine != null && StringUtils.isNotBlank(typeDefine.getColumnType())) {
            return typeDefine.getColumnType();
        }

        // Fallback to basic type mapping
        switch (column.getDataType().getSqlType()) {
            case BOOLEAN:
                return "BOOLEAN";
            case TINYINT:
                return "INT1";
            case SMALLINT:
                return "SMALLINT";
            case INT:
                return "INTEGER";
            case BIGINT:
                return "BIGINT";
            case FLOAT:
                return "REAL";
            case DOUBLE:
                return "DOUBLE PRECISION";
            case DECIMAL:
                if (column.getScale() != null && column.getScale() > 0) {
                    return String.format(
                            "DECIMAL(%d,%d)",
                            column.getColumnLength() != null
                                    ? column.getColumnLength().intValue()
                                    : 38,
                            column.getScale());
                } else {
                    return String.format(
                            "DECIMAL(%d)",
                            column.getColumnLength() != null
                                    ? column.getColumnLength().intValue()
                                    : 38);
                }
            case STRING:
                if (column.getColumnLength() != null && column.getColumnLength() > 0) {
                    return String.format("VARCHAR(%d)", column.getColumnLength().intValue());
                } else {
                    return "TEXT";
                }
            case DATE:
                return "DATE";
            case TIME:
                return "TIME";
            case TIMESTAMP:
                return "TIMESTAMP";
            case BYTES:
                return "BYTEA";
            default:
                return "TEXT";
        }
    }

    private String buildPrimaryKeySql() {
        String columnNamesString =
                primaryKey.getColumnNames().stream()
                        .map(columnName -> "\"" + columnName + "\"")
                        .collect(Collectors.joining(", "));
        return String.format("PRIMARY KEY (%s)", columnNamesString);
    }

    private String buildUniqueKeySql(ConstraintKey constraintKey) {
        String columnNamesString =
                constraintKey.getColumnNames().stream()
                        .map(columnName -> "\"" + columnName + "\"")
                        .collect(Collectors.joining(", "));
        return String.format(
                "CONSTRAINT \"%s\" UNIQUE (%s)",
                constraintKey.getConstraintName(), columnNamesString);
    }

    private String buildIndexKeySql(ConstraintKey constraintKey, TablePath tablePath) {
        String columnNamesString =
                constraintKey.getColumnNames().stream()
                        .map(columnName -> "\"" + columnName + "\"")
                        .collect(Collectors.joining(", "));
        String indexName = constraintKey.getConstraintName();
        if (StringUtils.isBlank(indexName)) {
            indexName = "idx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        return String.format(
                "CREATE INDEX \"%s\" ON %s (%s)",
                indexName, tablePath.getSchemaAndTableName("\""), columnNamesString);
    }
}

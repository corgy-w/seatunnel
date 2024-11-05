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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.dm;

import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.api.table.converter.TypeConverter;
import org.apache.seatunnel.api.table.schema.event.AlterTableAddColumnEvent;
import org.apache.seatunnel.api.table.schema.event.AlterTableChangeColumnEvent;
import org.apache.seatunnel.api.table.schema.event.AlterTableDropColumnEvent;
import org.apache.seatunnel.api.table.schema.event.AlterTableModifyColumnEvent;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.converter.JdbcRowConverter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.JdbcDialect;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.JdbcDialectTypeMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class DmdbDialect implements JdbcDialect {

    public String fieldIde;

    public DmdbDialect(String fieldIde) {
        this.fieldIde = fieldIde;
    }

    @Override
    public TypeConverter<BasicTypeDefine> typeConverter() {
        return (TypeConverter) DmdbTypeConverter.INSTANCE;
    }

    @Override
    public String dialectName() {
        return DatabaseIdentifier.DAMENG;
    }

    @Override
    public JdbcRowConverter getRowConverter() {
        return new DmdbJdbcRowConverter();
    }

    @Override
    public JdbcDialectTypeMapper getJdbcDialectTypeMapper() {
        return new DmdbTypeMapper();
    }

    @Override
    public Optional<String> getUpsertStatement(
            String database,
            String tableName,
            String[] fieldNames,
            String[] uniqueKeyFields,
            boolean isPrimaryKeyUpdated) {
        List<String> nonUniqueKeyFields =
                Arrays.stream(fieldNames)
                        .filter(fieldName -> !Arrays.asList(uniqueKeyFields).contains(fieldName))
                        .collect(Collectors.toList());
        String valuesBinding =
                Arrays.stream(fieldNames)
                        .map(fieldName -> ":" + fieldName + " " + quoteIdentifier(fieldName))
                        .collect(Collectors.joining(", "));
        String usingClause = String.format("SELECT %s", valuesBinding);
        String onConditions =
                Arrays.stream(uniqueKeyFields)
                        .map(
                                fieldName ->
                                        String.format(
                                                "TARGET.%s=SOURCE.%s",
                                                quoteIdentifier(fieldName),
                                                quoteIdentifier(fieldName)))
                        .collect(Collectors.joining(" AND "));

        String updateSetClause =
                nonUniqueKeyFields.stream()
                        .map(
                                fieldName ->
                                        String.format(
                                                "TARGET.%s=SOURCE.%s",
                                                quoteIdentifier(fieldName),
                                                quoteIdentifier(fieldName)))
                        .collect(Collectors.joining(", "));

        String insertFields =
                Arrays.stream(fieldNames)
                        .map(this::quoteIdentifier)
                        .collect(Collectors.joining(", "));
        String insertValues =
                Arrays.stream(fieldNames)
                        .map(fieldName -> "SOURCE." + quoteIdentifier(fieldName))
                        .collect(Collectors.joining(", "));
        // If there is a schema in the sql of dm, an error will be reported.
        // This is compatible with the case that the schema is written or not written in the conf
        // configuration file
        String databaseName =
                database == null
                        ? quoteIdentifier(tableName)
                        : (tableName.contains(".")
                                ? quoteIdentifier(tableName)
                                : tableIdentifier(database, tableName));
        String upsertSQL =
                String.format(
                        " MERGE INTO %s TARGET"
                                + " USING (%s) SOURCE"
                                + " ON (%s) "
                                + " WHEN MATCHED THEN"
                                + " UPDATE SET %s"
                                + " WHEN NOT MATCHED THEN"
                                + " INSERT (%s) VALUES (%s)",
                        databaseName,
                        usingClause,
                        onConditions,
                        updateSetClause,
                        insertFields,
                        insertValues);

        return Optional.of(upsertSQL);
    }

    @Override
    public String getDeleteStatement(String database, String tableName, String[] conditionFields) {
        String conditionClause =
                Arrays.stream(conditionFields)
                        .map(fieldName -> format("%s = :%s", quoteIdentifier(fieldName), fieldName))
                        .collect(Collectors.joining(" AND "));
        String databaseName =
                database == null
                        ? quoteIdentifier(tableName)
                        : (tableName.contains(".")
                                ? quoteIdentifier(tableName)
                                : tableIdentifier(database, tableName));

        return String.format("DELETE FROM %s WHERE %s", databaseName, conditionClause);
    }

    @Override
    public String extractTableName(TablePath tablePath) {
        return tablePath.getSchemaAndTableName();
    }

    @Override
    public TablePath parse(String tablePath) {
        return TablePath.of(tablePath, true);
    }

    @Override
    public String tableIdentifier(TablePath tablePath) {
        return tablePath.getSchemaAndTableName("\"");
    }

    // Compatibility Both database = mode and table-names = schema.tableName are configured
    @Override
    public String tableIdentifier(String database, String tableName) {
        if (tableName.contains(".")) {
            return quoteIdentifier(tableName);
        }
        return quoteDatabaseIdentifier(database) + "." + quoteIdentifier(tableName);
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier.contains(".")) {
            String[] parts = identifier.split("\\.");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                sb.append("\"").append(parts[i]).append("\"").append(".");
            }
            return sb.append("\"")
                    .append(getFieldIde(parts[parts.length - 1], fieldIde))
                    .append("\"")
                    .toString();
        }

        return "\"" + getFieldIde(identifier, fieldIde) + "\"";
    }

    @Override
    public void applySchemaChange(
            Connection connection, TablePath tablePath, AlterTableAddColumnEvent event)
            throws SQLException {

        boolean someCatalog = event.tableIdentifier().getCatalogName().equals(dialectName());
        BasicTypeDefine typeDefine = typeConverter().reconvert(event.getColumn());
        String columnType =
                someCatalog ? event.getColumn().getSourceType() : typeDefine.getColumnType();
        StringBuilder sqlBuilder =
                new StringBuilder()
                        .append("ALTER TABLE")
                        .append(" ")
                        .append(tableIdentifier(tablePath))
                        .append(" ")
                        .append("ADD")
                        .append(" ")
                        .append(quoteIdentifier(event.getColumn().getName()))
                        .append(" ")
                        .append(columnType)
                        .append(" ")
                        .append(event.getColumn().isNullable() ? "NULL" : "NOT NULL");
        if (event.getColumn().getDefaultValue() != null) {
            sqlBuilder.append(" ").append(sqlClauseWithDefaultValue(typeDefine));
        }
        String addColumnSQL = sqlBuilder.toString();
        try (Statement statement = connection.createStatement()) {
            log.info("Executing add column SQL: " + addColumnSQL);
            statement.execute(addColumnSQL);
            addComment(
                    statement,
                    event.getColumn().getComment(),
                    tablePath,
                    quoteIdentifier(event.getColumn().getName()));
        }
    }

    @Override
    public void applySchemaChange(
            Connection connection, TablePath tablePath, AlterTableChangeColumnEvent event)
            throws SQLException {
        StringBuilder sqlBuilder =
                new StringBuilder()
                        .append("ALTER TABLE")
                        .append(" ")
                        .append(tableIdentifier(tablePath))
                        .append(" ")
                        .append("RENAME COLUMN")
                        .append(" ")
                        .append(quoteIdentifier(event.getOldColumn()))
                        .append(" TO ")
                        .append(quoteIdentifier(event.getColumn().getName()));
        String changeColumnSQL = sqlBuilder.toString();
        try (Statement statement = connection.createStatement()) {
            log.info("Executing change column SQL: " + changeColumnSQL);
            statement.execute(changeColumnSQL);
        }
    }

    @Override
    public void applySchemaChange(
            Connection connection, TablePath tablePath, AlterTableModifyColumnEvent event)
            throws SQLException {

        boolean someCatalog = event.tableIdentifier().getCatalogName().equals(dialectName());
        BasicTypeDefine typeDefine = typeConverter().reconvert(event.getColumn());
        String columnType =
                someCatalog ? event.getColumn().getSourceType() : typeDefine.getColumnType();
        StringBuilder sqlBuilder =
                new StringBuilder()
                        .append("ALTER TABLE")
                        .append(" ")
                        .append(tableIdentifier(tablePath))
                        .append(" ")
                        .append("MODIFY")
                        .append(" ")
                        .append(quoteIdentifier(event.getColumn().getName()))
                        .append(" ")
                        .append(columnType)
                        .append(" ")
                        .append(event.getColumn().isNullable() ? "NULL" : "NOT NULL");
        if (event.getColumn().getDefaultValue() != null) {
            sqlBuilder.append(" ").append(sqlClauseWithDefaultValue(typeDefine));
        }
        String modifyColumnSQL = sqlBuilder.toString();
        try (Statement statement = connection.createStatement()) {
            log.info("Executing modify column SQL: " + modifyColumnSQL);
            statement.execute(modifyColumnSQL);
            addComment(
                    statement,
                    event.getColumn().getComment(),
                    tablePath,
                    quoteIdentifier(event.getColumn().getName()));
        }
    }

    @Override
    public void applySchemaChange(
            Connection connection, TablePath tablePath, AlterTableDropColumnEvent event)
            throws SQLException {
        String dropColumnSQL =
                String.format(
                        "ALTER TABLE %s DROP COLUMN %s",
                        tableIdentifier(tablePath), quoteIdentifier(event.getColumn()));
        try (Statement statement = connection.createStatement()) {
            log.info("Executing drop column SQL: " + dropColumnSQL);
            statement.execute(dropColumnSQL);
        }
    }

    private void addComment(
            Statement statement, String comment, TablePath tablePath, String columnName) {
        if (comment != null) {
            String addCommentSql =
                    String.format(
                            "comment on column %s.%s is '%s'",
                            tableIdentifier(tablePath), quoteIdentifier(columnName), comment);
            log.info("Executing add column comment SQL: " + addCommentSql);
            try {
                statement.execute(addCommentSql);
            } catch (SQLException exception) {
                log.warn("Executing add column comment SQL fail : {}", addCommentSql);
            }
        }
    }
}

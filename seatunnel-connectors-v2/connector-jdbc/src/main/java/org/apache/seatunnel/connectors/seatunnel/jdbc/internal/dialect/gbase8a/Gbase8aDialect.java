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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.gbase8a;

import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.converter.TypeConverter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.converter.JdbcRowConverter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.JdbcDialect;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.JdbcDialectTypeMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Optional;

public class Gbase8aDialect implements JdbcDialect {
    @Override
    public String dialectName() {
        return DatabaseIdentifier.GBASE_8A;
    }

    @Override
    @SuppressWarnings("unchecked")
    public TypeConverter typeConverter() {
        return Gbase8aTypeConverter.INSTANCE;
    }

    @Override
    public JdbcRowConverter getRowConverter() {
        return new Gbase8aJdbcRowConverter();
    }

    @Override
    public JdbcDialectTypeMapper getJdbcDialectTypeMapper() {
        return new Gbase8aTypeMapper();
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + identifier + "`";
    }

    @Override
    public String quoteDatabaseIdentifier(String identifier) {
        return "`" + identifier + "`";
    }

    @Override
    public Optional<String> getUpsertStatement(
            String database,
            String tableName,
            String[] fieldNames,
            String[] uniqueKeyFields,
            boolean isPrimaryKeyUpdated) {
        // Gbase8a does NOT support MySQL's ON DUPLICATE KEY UPDATE syntax
        // Testing shows that Gbase8a treats this as a normal INSERT and creates duplicate rows
        // instead of updating existing rows
        return Optional.empty();
    }

    @Override
    public ResultSetMetaData getResultSetMetaData(Connection conn, String query)
            throws SQLException {
        String metadataQuery = String.format("SELECT * FROM (%s) AS temp WHERE 1 = 0", query);
        try (PreparedStatement preparedStatement = conn.prepareStatement(metadataQuery)) {
            return preparedStatement.getMetaData();
        }
    }

    @Override
    public String tableIdentifier(TablePath tablePath) {
        return tablePath.getDatabaseName() != null
                ? quoteDatabaseIdentifier(tablePath.getDatabaseName())
                        + "."
                        + quoteIdentifier(tablePath.getTableName())
                : quoteIdentifier(tablePath.getTableName());
    }

    @Override
    public String getExistTableSql(TablePath tablePath) {
        return String.format(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'",
                tablePath.getDatabaseName(), tablePath.getTableName());
    }
}

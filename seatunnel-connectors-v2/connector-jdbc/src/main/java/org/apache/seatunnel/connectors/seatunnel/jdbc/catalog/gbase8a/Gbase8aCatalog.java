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

package org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.gbase8a;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.ConstraintKey;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.catalog.exception.CatalogException;
import org.apache.seatunnel.api.table.catalog.exception.DatabaseNotExistException;
import org.apache.seatunnel.api.table.catalog.exception.TableNotExistException;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.common.utils.JdbcUrlUtil;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.AbstractJdbcCatalog;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.utils.CatalogUtils;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.gbase8a.Gbase8aTypeConverter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.gbase8a.Gbase8aTypeMapper;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Slf4j
public class Gbase8aCatalog extends AbstractJdbcCatalog {

    private static final String SELECT_COLUMNS_SQL_TEMPLATE =
            "SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME ='%s' ORDER BY ORDINAL_POSITION ASC";

    private static final String SELECT_DATABASE_EXISTS =
            "SELECT SCHEMA_NAME FROM information_schema.schemata WHERE SCHEMA_NAME = '%s'";

    private static final String SELECT_TABLE_EXISTS =
            "SELECT TABLE_SCHEMA,TABLE_NAME FROM information_schema.tables WHERE table_schema = '%s' AND table_name = '%s'";

    public Gbase8aCatalog(
            String catalogName, String username, String pwd, JdbcUrlUtil.UrlInfo urlInfo) {
        super(catalogName, username, pwd, urlInfo, null);
    }

    @Override
    protected String getDatabaseWithConditionSql(String databaseName) {
        return String.format(SELECT_DATABASE_EXISTS, databaseName);
    }

    @Override
    protected String getTableWithConditionSql(TablePath tablePath) {
        return String.format(
                SELECT_TABLE_EXISTS, tablePath.getDatabaseName(), tablePath.getTableName());
    }

    @Override
    protected String getListDatabaseSql() {
        return "SHOW DATABASES;";
    }

    @Override
    protected String getListTableSql(String databaseName) {
        return "SHOW TABLES;";
    }

    @Override
    protected String getTableName(ResultSet rs) throws SQLException {
        return rs.getString(1);
    }

    @Override
    protected String getTableName(TablePath tablePath) {
        return tablePath.getTableName();
    }

    @Override
    protected String getSelectColumnsSql(TablePath tablePath) {
        return String.format(
                SELECT_COLUMNS_SQL_TEMPLATE, tablePath.getDatabaseName(), tablePath.getTableName());
    }

    @Override
    protected TableIdentifier getTableIdentifier(TablePath tablePath) {
        return TableIdentifier.of(
                catalogName, tablePath.getDatabaseName(), tablePath.getTableName());
    }

    @Override
    protected List<ConstraintKey> getConstraintKeys(DatabaseMetaData metaData, TablePath tablePath)
            throws SQLException {
        List<ConstraintKey> indexList =
                super.getConstraintKeys(
                        metaData,
                        tablePath.getDatabaseName(),
                        tablePath.getSchemaName(),
                        tablePath.getTableName());
        for (Iterator<ConstraintKey> it = indexList.iterator(); it.hasNext(); ) {
            ConstraintKey index = it.next();
            if (ConstraintKey.ConstraintType.UNIQUE_KEY.equals(index.getConstraintType())
                    && "PRIMARY".equals(index.getConstraintName())) {
                it.remove();
            }
        }
        return indexList;
    }

    @Override
    protected Column buildColumn(ResultSet resultSet) throws SQLException {
        String columnName = resultSet.getString("COLUMN_NAME");
        String dataType = resultSet.getString("DATA_TYPE");
        String columnType = resultSet.getString("COLUMN_TYPE");
        long columnLength = resultSet.getLong("CHARACTER_MAXIMUM_LENGTH");
        if (resultSet.wasNull()) {
            columnLength = resultSet.getLong("NUMERIC_PRECISION");
        }
        int columnScale = resultSet.getInt("NUMERIC_SCALE");
        String columnComment = resultSet.getString("COLUMN_COMMENT");
        Object defaultValue = resultSet.getObject("COLUMN_DEFAULT");
        String isNullableStr = resultSet.getString("IS_NULLABLE");
        boolean isNullable = "YES".equalsIgnoreCase(isNullableStr);

        BasicTypeDefine typeDefine =
                BasicTypeDefine.builder()
                        .name(columnName)
                        .columnType(columnType)
                        .dataType(dataType)
                        .length(columnLength)
                        .precision(columnLength)
                        .scale(columnScale)
                        .nullable(isNullable)
                        .defaultValue(defaultValue)
                        .comment(columnComment)
                        .build();
        return Gbase8aTypeConverter.INSTANCE.convert(typeDefine);
    }

    @Override
    protected String getCreateTableSql(TablePath tablePath, CatalogTable table) {
        return Gbase8aCreateTableSqlBuilder.builder(
                        tablePath, table, Gbase8aTypeConverter.INSTANCE, null)
                .build(catalogName);
    }

    @Override
    protected String getDropTableSql(TablePath tablePath) {
        return "DROP TABLE IF EXISTS "
                + tablePath.getDatabaseName()
                + "."
                + tablePath.getTableName();
    }

    @Override
    protected String getTruncateTableSql(TablePath tablePath) {
        return String.format(
                "TRUNCATE TABLE %s.%s", tablePath.getDatabaseName(), tablePath.getTableName());
    }

    @Override
    protected String getCreateDatabaseSql(String databaseName) {
        return "CREATE DATABASE `" + databaseName + "`";
    }

    @Override
    protected String getDropDatabaseSql(String databaseName) {
        return "DROP DATABASE IF EXISTS `" + databaseName + "`";
    }

    @Override
    public String getExistDataSql(TablePath tablePath) {
        return String.format(
                "SELECT * FROM %s.%s LIMIT 1",
                tablePath.getDatabaseName(), tablePath.getTableName());
    }

    @Override
    protected Connection getConnection(String url) {
        if (connectionMap.containsKey(url)) {
            Connection cachedConn = connectionMap.get(url);
            try {
                // Validate connection by checking if it's closed
                if (cachedConn != null && !cachedConn.isClosed()) {
                    // Try a simple validation query
                    if (cachedConn.isValid(2)) {
                        return cachedConn;
                    }
                }
            } catch (SQLException e) {
                // Connection is stale, remove from cache and create new one
                connectionMap.remove(url);
            }
        }
        try {
            Connection connection = DriverManager.getConnection(url, username, pwd);
            connectionMap.put(url, connection);
            return connection;
        } catch (SQLException e) {
            throw new CatalogException(String.format("Failed connecting to %s via JDBC.", url), e);
        }
    }

    @Override
    public CatalogTable getTable(String sqlQuery) throws SQLException {
        return CatalogUtils.getCatalogTable(
                getConnection(defaultUrl), sqlQuery, new Gbase8aTypeMapper());
    }

    @Override
    public List<String> listTables(String databaseName)
            throws CatalogException, DatabaseNotExistException {
        if (!databaseExists(databaseName)) {
            throw new DatabaseNotExistException(this.catalogName, databaseName);
        }

        String dbUrl = getUrlFromDatabaseName(databaseName);
        Connection connection = getConnection(dbUrl);
        try (PreparedStatement ps =
                        connection.prepareStatement(
                                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = '"
                                        + databaseName
                                        + "' AND TABLE_TYPE = 'BASE TABLE'");
                ResultSet rs = ps.executeQuery()) {

            List<String> tables = new ArrayList<>();

            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                tables.add(tableName);
            }

            return tables;
        } catch (Exception e) {
            throw new CatalogException(
                    String.format("Failed listing database in catalog %s", catalogName), e);
        }
    }

    @Override
    public CatalogTable getTable(TablePath tablePath)
            throws CatalogException, TableNotExistException {
        TablePath gbaseTablePath =
                TablePath.of(tablePath.getDatabaseName(), null, tablePath.getTableName());

        if (!tableExists(gbaseTablePath)) {
            throw new TableNotExistException(catalogName, tablePath);
        }

        String dbUrl;
        if (tablePath.getDatabaseName() != null) {
            dbUrl = getUrlFromDatabaseName(tablePath.getDatabaseName());
        } else {
            dbUrl = getUrlFromDatabaseName(defaultDatabase);
        }
        Connection conn = getConnection(dbUrl);
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            Optional<PrimaryKey> primaryKey =
                    getPrimaryKey(
                            metaData,
                            gbaseTablePath.getDatabaseName(),
                            gbaseTablePath.getSchemaName(),
                            gbaseTablePath.getTableName());
            List<ConstraintKey> constraintKeys =
                    getConstraintKeys(
                            metaData,
                            gbaseTablePath.getDatabaseName(),
                            gbaseTablePath.getSchemaName(),
                            gbaseTablePath.getTableName());
            constraintKeys = filterDuplicateConstraintKeys(constraintKeys, primaryKey);
            String tableComment = getTableComment(metaData, gbaseTablePath);
            try (ResultSet resultSet =
                    metaData.getColumns(
                            gbaseTablePath.getDatabaseName(),
                            gbaseTablePath.getSchemaName(),
                            gbaseTablePath.getTableName(),
                            null)) {
                TableSchema.Builder builder = TableSchema.builder();
                buildColumnsWithErrorCheck(tablePath, resultSet, builder);
                // add primary key
                primaryKey.ifPresent(builder::primaryKey);
                // add constraint key
                constraintKeys.forEach(builder::constraintKey);
                TableIdentifier tableIdentifier =
                        TableIdentifier.of(
                                catalogName,
                                tablePath.getDatabaseName(),
                                null,
                                tablePath.getTableName());
                return CatalogTable.of(
                        tableIdentifier,
                        builder.build(),
                        buildConnectorOptions(tablePath),
                        Collections.emptyList(),
                        tableComment,
                        catalogName);
            }

        } catch (Exception e) {
            throw new CatalogException(
                    String.format("Failed getting table %s", tablePath.getFullName()), e);
        }
    }

    @Override
    public boolean tableExists(TablePath tablePath) throws CatalogException {
        String dbUrl;
        if (tablePath.getDatabaseName() != null) {
            dbUrl = getUrlFromDatabaseName(tablePath.getDatabaseName());
        } else {
            dbUrl = defaultUrl;
        }
        String sql =
                String.format(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'",
                        tablePath.getDatabaseName() != null
                                ? tablePath.getDatabaseName()
                                : defaultDatabase,
                        tablePath.getTableName());

        try (Connection con = getConnection(dbUrl);
                Statement statement = con.createStatement();
                ResultSet rs = statement.executeQuery(sql)) {
            return rs.next();
        } catch (SQLException e) {
            throw new CatalogException("Failed to check table exists", e);
        }
    }
}

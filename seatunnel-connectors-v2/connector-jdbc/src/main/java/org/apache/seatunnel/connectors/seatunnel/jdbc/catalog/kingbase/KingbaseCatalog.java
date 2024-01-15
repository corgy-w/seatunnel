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

package org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.kingbase;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.catalog.exception.CatalogException;
import org.apache.seatunnel.api.table.catalog.exception.DatabaseNotExistException;
import org.apache.seatunnel.api.table.catalog.exception.TableNotExistException;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.common.utils.JdbcUrlUtil;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.AbstractJdbcCatalog;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.utils.CatalogUtils;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.kingbase.KingbaseTypeMapper;

import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KingbaseCatalog extends AbstractJdbcCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(KingbaseCatalog.class);

    public static final Set<String> KINGBASE_SYSTEM_DATABASES =
            Sets.newHashSet("TEMPLATE1", "TEMPLATE0", "TEMPLATE2", "SAMPLES", "SECURITY");

    public static final Set<String> KINGBASE_SYSTEM_SCHEMAS =
            Sets.newHashSet(
                    "information_schema",
                    "SYS_CATALOG",
                    "SYSAUDIT",
                    "SYS_HM",
                    "SYS_TOAST",
                    "SYS_TEMP_1",
                    "SYSLOGICAL",
                    "SYS_TOAST_TEMP_1");

    protected final Map<String, Connection> connectionMap;

    public KingbaseCatalog(
            String catalogName,
            String username,
            String pwd,
            JdbcUrlUtil.UrlInfo urlInfo,
            String defaultSchema) {
        super(catalogName, username, pwd, urlInfo, defaultSchema);
        this.connectionMap = new ConcurrentHashMap<>();
    }

    @Override
    public String getExistDataSql(TablePath tablePath) {
        return String.format("select * from %s LIMIT 1;", tablePath.getFullName());
    }

    @Override
    protected String getCreateTableSql(TablePath tablePath, CatalogTable table) {
        return new KingbaseCreateTableSqlBuilder(table)
                .build(tablePath, table.getOptions().get("fieldIde"));
    }

    @Override
    protected String getDropTableSql(TablePath tablePath) {
        String schemaName = tablePath.getSchemaName();
        String tableName = tablePath.getTableName();

        return "DROP TABLE IF EXISTS \"" + schemaName + "\".\"" + tableName + "\"";
    }

    @Override
    protected String getTruncateTableSql(TablePath tablePath) {
        String schemaName = tablePath.getSchemaName();
        String tableName = tablePath.getTableName();

        return "TRUNCATE TABLE  \"" + schemaName + "\".\"" + tableName + "\"";
    }

    @Override
    protected String getCreateDatabaseSql(String databaseName) {
        return "CREATE DATABASE \"" + databaseName + "\"";
    }

    @Override
    protected String getDropDatabaseSql(String databaseName) {
        return "DROP DATABASE IF EXISTS \"" + databaseName + "\"";
    }

    @Override
    public List<String> listDatabases() throws CatalogException {
        List<String> dbNames = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(defaultUrl, username, pwd);
                PreparedStatement statement =
                        connection.prepareStatement("select datname from sys_database;")) {
            ResultSet re = statement.executeQuery();
            while (re.next()) {
                String dbName = re.getString("datname");
                if (StringUtils.isNotBlank(dbName) && !KINGBASE_SYSTEM_DATABASES.contains(dbName)) {
                    dbNames.add(dbName);
                }
            }
            return dbNames;
        } catch (Exception e) {
            throw new CatalogException("get databases failed", e);
        }
    }

    @Override
    public List<String> listTables(String databaseName)
            throws CatalogException, DatabaseNotExistException {
        List<String> tableNames = new ArrayList<>();
        String query = "SELECT table_schema, table_name FROM information_schema.tables";
        String dbUrl = getUrlFromDatabaseName(databaseName);
        try (Connection connection = DriverManager.getConnection(dbUrl, username, pwd);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                String schemaName = resultSet.getString("table_schema");
                String tableName = resultSet.getString("table_name");
                if (StringUtils.isNotBlank(schemaName)
                        && !KINGBASE_SYSTEM_SCHEMAS.contains(schemaName)) {
                    tableNames.add(schemaName + "." + tableName);
                }
            }
            return tableNames;
        } catch (Exception e) {
            throw new CatalogException("get table names failed", e);
        }
    }

    @Override
    public boolean tableExists(TablePath tablePath) throws CatalogException {
        try {
            if (StringUtils.isNotBlank(tablePath.getDatabaseName())) {
                return databaseExists(tablePath.getDatabaseName())
                        && listTables(tablePath.getDatabaseName())
                                .contains(tablePath.getSchemaAndTableName());
            }

            return listTables(defaultDatabase).contains(tablePath.getSchemaAndTableName());
        } catch (DatabaseNotExistException e) {
            return false;
        }
    }

    @Override
    public CatalogTable getTable(TablePath tablePath)
            throws CatalogException, TableNotExistException {
        if (!tableExists(tablePath)) {
            throw new TableNotExistException(catalogName, tablePath);
        }

        String dbUrl;
        if (StringUtils.isBlank(tablePath.getDatabaseName())) {
            dbUrl = getUrlFromDatabaseName(defaultDatabase);
        } else {
            dbUrl = getUrlFromDatabaseName(tablePath.getDatabaseName());
        }
        TableSchema.Builder builder = TableSchema.builder();
        try (Connection connection = DriverManager.getConnection(dbUrl, username, pwd)) {
            DatabaseMetaData metaData = connection.getMetaData();
            Optional<PrimaryKey> primaryKey =
                    getPrimaryKey(
                            metaData,
                            tablePath.getDatabaseName(),
                            tablePath.getSchemaName(),
                            tablePath.getTableName());
            try (ResultSet resultSet =
                    metaData.getColumns(
                            tablePath.getDatabaseName(),
                            tablePath.getSchemaName(),
                            tablePath.getTableName(),
                            null)) {
                buildColumnsWithErrorCheck(tablePath, resultSet, builder);
            }
            primaryKey.ifPresent(builder::primaryKey);
            TableIdentifier tableIdentifier =
                    TableIdentifier.of(
                            catalogName,
                            tablePath.getDatabaseName(),
                            tablePath.getSchemaName(),
                            tablePath.getTableName());
            return CatalogTable.of(
                    tableIdentifier, builder.build(), new HashMap<>(), new ArrayList<>(), "");
        } catch (Exception e) {
            throw new CatalogException("get table fields failed", e);
        }
    }

    @Override
    protected Column buildColumn(ResultSet resultSet) throws SQLException {
        String pgType = resultSet.getString("TYPE_NAME").toUpperCase();
        String columnName = resultSet.getString("COLUMN_NAME");
        int columnDisplaySize = resultSet.getInt("COLUMN_SIZE");
        String defaultValue = resultSet.getString("COLUMN_DEF");
        int columnSize = resultSet.getInt("COLUMN_SIZE");
        int decimalDigits = resultSet.getInt("DECIMAL_DIGITS");
        int nullable = resultSet.getInt("NULLABLE");
        String remarks = resultSet.getString("REMARKS");

        return PhysicalColumn.of(
                columnName,
                fromJdbcType(columnName, pgType, columnSize, decimalDigits),
                columnDisplaySize,
                nullable != ResultSetMetaData.columnNoNulls,
                defaultValue,
                remarks,
                pgType,
                false,
                false,
                (long) columnSize << 2,
                new HashMap<>(),
                (long) columnSize);
    }

    public Connection getConnection(String url) {
        if (connectionMap.containsKey(url)) {
            return connectionMap.get(url);
        }
        try {
            Connection connection = DriverManager.getConnection(url, username, pwd);
            connectionMap.put(url, connection);
            return connection;
        } catch (SQLException e) {
            throw new CatalogException(String.format("Failed connecting to %s via JDBC.", url), e);
        }
    }

    private SeaTunnelDataType<?> fromJdbcType(
            String columnName, String typeName, long precision, long scale) {
        Map<String, Object> dataTypeProperties = new HashMap<>();
        dataTypeProperties.put(KingBaseDataTypeConvertor.PRECISION, precision);
        dataTypeProperties.put(KingBaseDataTypeConvertor.SCALE, scale);
        return new KingBaseDataTypeConvertor()
                .toSeaTunnelType(columnName, typeName, dataTypeProperties);
    }

    @Override
    public CatalogTable getTable(String sqlQuery) throws SQLException {
        return CatalogUtils.getCatalogTable(
                getConnection(getUrlFromDatabaseName(defaultDatabase)),
                sqlQuery,
                new KingbaseTypeMapper());
    }
}

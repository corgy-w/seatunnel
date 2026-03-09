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

package org.apache.seatunnel.connectors.dolphindb.sink.writter;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.dolphindb.catalog.DolphinDBSqlGenerator;

import com.dolphindb.jdbc.JDBCConnection;
import com.xxdb.comm.SqlStdEnum;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.ADDRESS;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.KEY_COL_NAMES;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.PASSWORD;
import static org.apache.seatunnel.connectors.dolphindb.config.DolphinDBConfig.USER;

public class DolphinDbDeleteWriter implements DolphinDBWriter {

    private final CatalogTable catalogTable;
    private final ReadonlyConfig pluginConfig;
    private final SeaTunnelRowType seaTunnelRowType;
    private final JDBCConnection dbConnection;
    private final int[] deleteConditionFieldIndexes;
    private final String deleteSql;

    public DolphinDbDeleteWriter(CatalogTable catalogTable, ReadonlyConfig pluginConfig)
            throws SQLException {
        this.catalogTable = catalogTable;
        this.pluginConfig = pluginConfig;
        this.seaTunnelRowType = catalogTable.getTableSchema().toPhysicalRowDataType();
        this.dbConnection = createDbConnection();
        this.deleteConditionFieldIndexes = resolveDeleteConditionFieldIndexes();
        this.dbConnection
                .createStatement()
                .execute(
                        catalogTable.getTableId().getTableName()
                                + " = loadTable(\""
                                + catalogTable.getTableId().getDatabaseName()
                                + "\", \""
                                + catalogTable.getTableId().getTableName()
                                + "\")");
        this.deleteSql =
                DolphinDBSqlGenerator.generateDeleteRowSql(
                        catalogTable.getTableId().getTableName(),
                        seaTunnelRowType,
                        deleteConditionFieldIndexes);
    }

    @Override
    public void write(SeaTunnelRow seaTunnelRow) {
        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(deleteSql)) {
            Object[] fields = seaTunnelRow.getFields();
            int arity = fields.length;
            for (int i = 0; i < deleteConditionFieldIndexes.length; i++) {
                int fieldIndex = deleteConditionFieldIndexes[i];
                if (fieldIndex >= arity) {
                    throw new IllegalArgumentException(
                            "DolphinDB delete requires key field index "
                                    + fieldIndex
                                    + " but row arity is "
                                    + arity
                                    + ". Please ensure CDC DELETE/UPDATE_BEFORE contains key columns.");
                }
                Object fieldValue = fields[fieldIndex];
                if (fieldValue == null) {
                    preparedStatement.setNull(i + 1, Types.NULL);
                } else if (fieldValue instanceof BigDecimal) {
                    preparedStatement.setObject(i + 1, ((BigDecimal) fieldValue).doubleValue());
                } else {
                    preparedStatement.setObject(i + 1, fieldValue);
                }
            }
            preparedStatement.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<Void> prepareCommit() throws Exception {
        return Optional.empty();
    }

    @Override
    public void close() throws Exception {
        try (JDBCConnection dbConnection1 = dbConnection) {}
    }

    private JDBCConnection createDbConnection() throws SQLException {
        List<String> addresses = pluginConfig.get(ADDRESS);
        Properties prop = new Properties();
        prop.setProperty("user", pluginConfig.get(USER));
        prop.setProperty("password", pluginConfig.get(PASSWORD));
        prop.setProperty("sqlStd", SqlStdEnum.DolphinDB.getName());
        String address = addresses.get(0);
        prop.setProperty("hostName", address.substring(0, address.lastIndexOf(":")));
        prop.setProperty("port", address.substring(address.lastIndexOf(":") + 1));

        String url = "jdbc:dolphindb://" + address;
        return new JDBCConnection(url, prop);
    }

    private int[] resolveDeleteConditionFieldIndexes() {
        List<String> keyColNames =
                pluginConfig.getOptional(KEY_COL_NAMES).orElse(Collections.emptyList());
        if (keyColNames.isEmpty()
                && catalogTable.getTableSchema().getPrimaryKey() != null
                && catalogTable.getTableSchema().getPrimaryKey().getColumnNames() != null) {
            keyColNames = catalogTable.getTableSchema().getPrimaryKey().getColumnNames();
        }

        String[] fieldNames = seaTunnelRowType.getFieldNames();
        if (keyColNames == null || keyColNames.isEmpty()) {
            int[] all = new int[fieldNames.length];
            for (int i = 0; i < fieldNames.length; i++) {
                all[i] = i;
            }
            return all;
        }

        int[] indexes = new int[keyColNames.size()];
        for (int i = 0; i < keyColNames.size(); i++) {
            String keyName =
                    MultithreadedTableWriterFactory.normalizeColumnName(keyColNames.get(i));
            if (keyName == null) {
                keyName = "";
            }
            int idx = findFieldIndex(fieldNames, keyName);
            if (idx < 0) {
                throw new IllegalArgumentException(
                        "Can't find key column '"
                                + keyColNames.get(i)
                                + "' (normalized: '"
                                + keyName
                                + "') in upstream row fields "
                                + String.join(",", fieldNames));
            }
            indexes[i] = idx;
        }
        return indexes;
    }

    private static int findFieldIndex(String[] fieldNames, String name) {
        for (int i = 0; i < fieldNames.length; i++) {
            if (fieldNames[i].equals(name)) {
                return i;
            }
        }
        for (int i = 0; i < fieldNames.length; i++) {
            if (fieldNames[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}

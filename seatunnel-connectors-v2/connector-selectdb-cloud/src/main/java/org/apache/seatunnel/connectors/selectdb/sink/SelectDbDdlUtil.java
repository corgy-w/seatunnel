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

package org.apache.seatunnel.connectors.selectdb.sink;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.event.AlterTableAddColumnEvent;
import org.apache.seatunnel.api.table.event.AlterTableChangeColumnEvent;
import org.apache.seatunnel.api.table.event.AlterTableColumnsEvent;
import org.apache.seatunnel.api.table.event.AlterTableDropColumnEvent;
import org.apache.seatunnel.api.table.event.AlterTableModifyColumnEvent;
import org.apache.seatunnel.api.table.event.SchemaChangeEvent;
import org.apache.seatunnel.connectors.selectdb.config.SelectDBConfig;

import org.apache.commons.collections4.CollectionUtils;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SelectDbDdlUtil {

    public static void executeDdl(
            SelectDBConfig selectDBConfig, SchemaChangeEvent event, CatalogTable catalogTable) {
        final List<String> ddlSqlList = getDdlSqlList(event, catalogTable);
        if (!CollectionUtils.isEmpty(ddlSqlList)) {
            executeDdlSql(ddlSqlList, selectDBConfig);
        }
    }

    private static List<String> getDdlSqlList(SchemaChangeEvent event, CatalogTable catalogTable) {
        TablePath tablePath = catalogTable.getTableId().toTablePath();
        return getSQLFromSchemaChangeEvent(tablePath, event);
    }

    private static void executeDdlSql(List<String> ddlSqlList, SelectDBConfig selectDBConfig) {
        try (Connection conn =
                DriverManager.getConnection(
                        selectDBConfig.getJdbcUrl(),
                        selectDBConfig.getUsername(),
                        selectDBConfig.getPassword())) {
            final Statement statement = conn.createStatement();
            for (String ddlSql : ddlSqlList) {
                statement.execute(ddlSql);
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("Nothing is changed")) {
                log.warn(e.getMessage(), e);
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    private static List<String> getSQLFromSchemaChangeEvent(
            TablePath tablePath, SchemaChangeEvent event) {
        List<String> sqlList = new ArrayList<>();
        if (event instanceof AlterTableColumnsEvent) {
            ((AlterTableColumnsEvent) event)
                    .getEvents()
                    .forEach(
                            column -> {
                                if (column instanceof AlterTableChangeColumnEvent) {
                                    String sql =
                                            String.format(
                                                    "alter table %s RENAME COLUMN %s %s",
                                                    tablePath.getFullName(),
                                                    ((AlterTableChangeColumnEvent) column)
                                                            .getOldColumn(),
                                                    ((AlterTableAddColumnEvent) column)
                                                            .getColumn()
                                                            .getName());
                                    sqlList.add(sql);
                                } else if (column instanceof AlterTableModifyColumnEvent) {
                                    String sql =
                                            String.format(
                                                    "alter table %s MODIFY COLUMN %s",
                                                    tablePath.getFullName(),
                                                    SelectDBSaveModeUtil.columnToSelectDBType(
                                                            ((AlterTableAddColumnEvent) column)
                                                                    .getColumn()));
                                    sqlList.add(sql);
                                } else if (column instanceof AlterTableAddColumnEvent) {
                                    String sql =
                                            String.format(
                                                    "alter table %s add column %s DEFAULT %s",
                                                    tablePath.getFullName(),
                                                    SelectDBSaveModeUtil.columnToSelectDBType(
                                                            ((AlterTableAddColumnEvent) column)
                                                                    .getColumn()),
                                                    getDefaultValue(
                                                            ((AlterTableAddColumnEvent) column)
                                                                    .getColumn()
                                                                    .getDefaultValue()));
                                    sqlList.add(sql);
                                } else if (column instanceof AlterTableDropColumnEvent) {
                                    String sql =
                                            String.format(
                                                    "alter table %s drop column %s",
                                                    tablePath.getFullName(),
                                                    ((AlterTableDropColumnEvent) column)
                                                            .getColumn());
                                    sqlList.add(sql);
                                } else {
                                    throw new UnsupportedOperationException(
                                            "Unsupported event: " + event);
                                }
                            });
        }
        return sqlList;
    }

    private static String getDefaultValue(Object defaultValue) {
        if (defaultValue == null) {
            return "null";
        }
        return String.format("\"%s\"", defaultValue.toString());
    }
}

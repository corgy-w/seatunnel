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

package org.apache.seatunnel.connectors.seatunnel.snowflakefile.catalog;

import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;

import java.util.ArrayList;
import java.util.List;

public class SnowflakeCreateTableSqlBuilder {

    private static final String CREATE_TABLE_TEMPLATE = "CREATE TABLE %s (%s)";
    private static final String CREATE_TABLE_IF_NOT_EXISTS_TEMPLATE =
            "CREATE TABLE IF NOT EXISTS %s (%s)";

    private final TablePath tablePath;
    private final TableSchema tableSchema;
    private final boolean ifNotExists;

    public SnowflakeCreateTableSqlBuilder(
            TablePath tablePath, TableSchema tableSchema, boolean ifNotExists) {
        this.tablePath = tablePath;
        this.tableSchema = tableSchema;
        this.ifNotExists = ifNotExists;
    }

    public String build() {
        String fullTableName = tablePath.getFullName();
        String columns = buildColumns();

        String template = ifNotExists ? CREATE_TABLE_IF_NOT_EXISTS_TEMPLATE : CREATE_TABLE_TEMPLATE;
        return String.format(template, fullTableName, columns);
    }

    private String buildColumns() {
        List<String> columnDefinitions = new ArrayList<>();

        for (Column column : tableSchema.getColumns()) {
            String columnDefinition = buildColumnDefinition(column);
            columnDefinitions.add(columnDefinition);
        }

        return String.join(", ", columnDefinitions);
    }

    private String buildColumnDefinition(Column column) {
        StringBuilder sb = new StringBuilder();
        sb.append(column.getName()).append(" ");

        // 获取Snowflake数据类型
        String snowflakeType = convertToSnowflakeType(column);
        sb.append(snowflakeType);

        // 处理可空性
        if (!column.isNullable()) {
            sb.append(" NOT NULL");
        }

        // 处理默认值
        if (column.getDefaultValue() != null) {
            sb.append(" DEFAULT ")
                    .append(
                            formatDefaultValue(
                                    column.getDefaultValue(), column.getDataType().getSqlType()));
        }

        // 处理注释
        if (column.getComment() != null && !column.getComment().isEmpty()) {
            sb.append(" COMMENT '").append(escapeString(column.getComment())).append("'");
        }

        return sb.toString();
    }

    private String convertToSnowflakeType(Column column) {
        // 使用SnowflakeTypeConverter进行类型转换
        BasicTypeDefine typeDefine = SnowflakeTypeConverter.INSTANCE.reconvert(column);
        return typeDefine.getColumnType();
    }

    private String formatDefaultValue(
            Object defaultValue, org.apache.seatunnel.api.table.type.SqlType sqlType) {
        if (defaultValue == null) {
            return "NULL";
        }

        switch (sqlType) {
            case STRING:
            case DATE:
            case TIME:
            case TIMESTAMP:
                return "'" + escapeString(defaultValue.toString()) + "'";
            case BOOLEAN:
                return Boolean.parseBoolean(defaultValue.toString()) ? "TRUE" : "FALSE";
            default:
                return defaultValue.toString();
        }
    }

    private String escapeString(String str) {
        return str.replace("'", "''");
    }

    public static SnowflakeCreateTableSqlBuilder builder() {
        return new SnowflakeCreateTableSqlBuilder(null, null, false);
    }

    public SnowflakeCreateTableSqlBuilder tablePath(TablePath tablePath) {
        return new SnowflakeCreateTableSqlBuilder(tablePath, this.tableSchema, this.ifNotExists);
    }

    public SnowflakeCreateTableSqlBuilder tableSchema(TableSchema tableSchema) {
        return new SnowflakeCreateTableSqlBuilder(this.tablePath, tableSchema, this.ifNotExists);
    }

    public SnowflakeCreateTableSqlBuilder ifNotExists(boolean ifNotExists) {
        return new SnowflakeCreateTableSqlBuilder(this.tablePath, this.tableSchema, ifNotExists);
    }
}

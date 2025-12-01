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

package org.apache.seatunnel.connectors.seatunnel.common.util;

import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.DecimalType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CatalogUtilTest {

    /**
     * Test CatalogUtil to handle special characters in column comments. This test verifies that the
     * replaceAll method correctly handles regex special characters like $ and \ in replacement
     * strings.
     */
    @Test
    public void testGetCreateTableSqlWithSpecialCharacters() {
        CatalogUtil catalogUtil = new TestCatalogUtil();

        List<Column> columns = new ArrayList<>();

        columns.add(
                PhysicalColumn.of(
                        "decimal_col",
                        new DecimalType(10, 2),
                        10L,
                        2,
                        true,
                        null,
                        "Comment with $1 and $2 special chars"));

        columns.add(
                PhysicalColumn.of(
                        "string_col",
                        BasicType.STRING_TYPE,
                        255L,
                        true,
                        null,
                        "Comment with \\ backslash"));

        columns.add(
                PhysicalColumn.of(
                        "mixed_col",
                        BasicType.STRING_TYPE,
                        255L,
                        true,
                        null,
                        "~`!@#$%^&*()_+-*/-=[]{};\\':\",.<>?"));

        columns.add(
                PhysicalColumn.of(
                        "chinese_col",
                        BasicType.STRING_TYPE,
                        255L,
                        true,
                        null,
                        "这是特殊符号测试英文键盘：~`!@#$%^&*()_+-*/-=[]{};'':\",./<>?"));

        TableSchema tableSchema =
                TableSchema.builder()
                        .columns(columns)
                        .constraintKey(Collections.emptyList())
                        .build();

        String template =
                "CREATE TABLE IF NOT EXISTS `${database}`.`${table}` (\n"
                        + "${rowtype_fields}\n"
                        + ") ENGINE = MergeTree()\n"
                        + "COMMENT '${comment}';";

        String sql =
                catalogUtil.getCreateTableSql(
                        template, "test_db", "test_table", tableSchema, "table comment", "options");

        Assertions.assertTrue(sql.contains("test_db"));
        Assertions.assertTrue(sql.contains("test_table"));
        Assertions.assertTrue(sql.contains("decimal_col"));
        Assertions.assertTrue(sql.contains("string_col"));
        Assertions.assertTrue(sql.contains("mixed_col"));
        Assertions.assertTrue(sql.contains("chinese_col"));
        Assertions.assertTrue(sql.contains("table comment"));
    }

    /**
     * Test with the exact scenario from the bug report - ClickHouse auto create table with special
     * characters in column comments.
     */
    @Test
    public void testClickHouseAutoCreateTableWithSpecialComments() {
        CatalogUtil catalogUtil = new TestCatalogUtil();

        List<Column> columns = new ArrayList<>();

        columns.add(
                PhysicalColumn.of(
                        "tinyint_col", BasicType.INT_TYPE, (Long) null, true, null, "这是测试中文注释"));

        columns.add(
                PhysicalColumn.of(
                        "smallint_col",
                        BasicType.INT_TYPE,
                        (Long) null,
                        true,
                        null,
                        "这是特殊符号测试中文键盘：~！@#￥%……&*（）——+-*/【】；'，。、{}：\"《》？"));

        columns.add(
                PhysicalColumn.of(
                        "mediumint_col",
                        BasicType.INT_TYPE,
                        (Long) null,
                        true,
                        null,
                        "这是特殊符号测试英文键盘：~`!@#$%^&*()_+-*/-=[]{}"));

        columns.add(
                PhysicalColumn.of(
                        "bigint_col", BasicType.LONG_TYPE, (Long) null, true, null, "'单引号'"));

        columns.add(
                PhysicalColumn.of(
                        "unsigned_int_col",
                        BasicType.LONG_TYPE,
                        (Long) null,
                        true,
                        null,
                        "\"双引号\""));

        columns.add(
                PhysicalColumn.of(
                        "decimal_col", new DecimalType(10, 2), 10L, 2, true, null, "'英文1'"));

        columns.add(
                PhysicalColumn.of(
                        "float_col", BasicType.FLOAT_TYPE, (Long) null, true, null, "\"英文2\""));

        columns.add(
                PhysicalColumn.of(
                        "double_col",
                        BasicType.DOUBLE_TYPE,
                        (Long) null,
                        true,
                        null,
                        "'英文1',\"英文2\""));

        TableSchema tableSchema =
                TableSchema.builder()
                        .columns(columns)
                        .constraintKey(Collections.emptyList())
                        .build();

        String template =
                "CREATE TABLE IF NOT EXISTS `${database}`.`${table}` (\n"
                        + "`int_col` Int32 COMMENT 'int comment',\n"
                        + "${rowtype_fields}\n"
                        + ") ENGINE = ReplacingMergeTree()\n"
                        + "ORDER BY (`int_col`)\n"
                        + "PRIMARY KEY (`int_col`)\n"
                        + "SETTINGS\n"
                        + "    index_granularity = 8192\n"
                        + "COMMENT '${comment}';";

        String sql =
                catalogUtil.getCreateTableSql(
                        template, "test_db", "test_table", tableSchema, "table_comment", "options");

        Assertions.assertNotNull(sql);
        Assertions.assertTrue(sql.contains("test_db"));
        Assertions.assertTrue(sql.contains("test_table"));
        Assertions.assertTrue(sql.contains("tinyint_col"));
        Assertions.assertTrue(sql.contains("decimal_col"));
        Assertions.assertTrue(sql.contains("`int_col` Int32 COMMENT 'int comment'"));
    }

    @Test
    public void testGetCreateTableSqlWithPrimaryKey() {
        CatalogUtil catalogUtil = new TestCatalogUtil();

        List<Column> columns = new ArrayList<>();
        columns.add(
                PhysicalColumn.of(
                        "id", BasicType.LONG_TYPE, (Long) null, false, null, "Primary key column"));
        columns.add(
                PhysicalColumn.of(
                        "data",
                        BasicType.STRING_TYPE,
                        255L,
                        true,
                        null,
                        "Data with $pecial chars"));

        PrimaryKey primaryKey = PrimaryKey.of("pk_test", Arrays.asList("id"));

        TableSchema tableSchema =
                TableSchema.builder()
                        .columns(columns)
                        .primaryKey(primaryKey)
                        .constraintKey(Collections.emptyList())
                        .build();

        String template =
                "CREATE TABLE IF NOT EXISTS `${database}`.`${table}` (\n"
                        + "${rowtype_fields}\n"
                        + ") PRIMARY KEY (${rowtype_primary_key})\n"
                        + "COMMENT '${comment}';";

        String sql =
                catalogUtil.getCreateTableSql(
                        template, "test_db", "test_table", tableSchema, "comment", "options");

        Assertions.assertNotNull(sql);
        Assertions.assertTrue(sql.contains("`id`"));
    }

    static class TestCatalogUtil extends CatalogUtil {

        @Override
        public String columnToConnectorType(Column column) {
            StringBuilder sb = new StringBuilder();
            sb.append("`").append(column.getName()).append("` ");

            if (column.isNullable()) {
                sb.append("Nullable(");
            }

            String typeName = mapType(column);
            sb.append(typeName);

            if (column.isNullable()) {
                sb.append(")");
            }

            if (column.getComment() != null && !column.getComment().isEmpty()) {
                String escapedComment = column.getComment().replace("'", "\\'");
                sb.append(" COMMENT '").append(escapedComment).append("'");
            }

            return sb.toString();
        }

        private String mapType(Column column) {
            if (column.getDataType() instanceof DecimalType) {
                DecimalType decimalType = (DecimalType) column.getDataType();
                return String.format(
                        "Decimal(%d, %d)", decimalType.getPrecision(), decimalType.getScale());
            }

            switch (column.getDataType().getSqlType()) {
                case STRING:
                    return "String";
                case INT:
                    return "Int32";
                case BIGINT:
                    return "Int64";
                case FLOAT:
                    return "Float32";
                case DOUBLE:
                    return "Float64";
                case BOOLEAN:
                    return "Bool";
                default:
                    return "String";
            }
        }
    }
}

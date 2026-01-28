/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.db2;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.ConstraintKey;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.BasicType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

public class DB2CatalogTest {

    private static final CatalogTable CATALOG_TABLE =
            CatalogTable.of(
                    TableIdentifier.of("catalog", "database", "table"),
                    TableSchema.builder()
                            .columns(
                                    Arrays.asList(
                                            PhysicalColumn.of(
                                                    "test",
                                                    BasicType.STRING_TYPE,
                                                    (Long) null,
                                                    true,
                                                    null,
                                                    ""),
                                            PhysicalColumn.of(
                                                    "test2",
                                                    BasicType.STRING_TYPE,
                                                    (Long) null,
                                                    true,
                                                    null,
                                                    ""),
                                            PhysicalColumn.of(
                                                    "test3",
                                                    BasicType.STRING_TYPE,
                                                    (Long) null,
                                                    true,
                                                    null,
                                                    "")))
                            .primaryKey(
                                    new PrimaryKey(
                                            "test_primary_keys", Arrays.asList("test", "test2")))
                            .build(),
                    Collections.emptyMap(),
                    Collections.emptyList(),
                    "comment");

    @Test
    void testCreateTableSqlWithPrimaryKeys() {
        DB2CatalogFactory factory = new DB2CatalogFactory();
        DB2Catalog catalog =
                (DB2Catalog)
                        factory.createCatalog(
                                "test",
                                ReadonlyConfig.fromMap(
                                        new HashMap<String, Object>() {
                                            {
                                                put(
                                                        "base-url",
                                                        "jdbc:kingbase://localhost:5432/test");
                                                put("username", "test");
                                                put("password", "test");
                                            }
                                        }));
        String sql = catalog.getCreateTableSql(TablePath.of("test.test.test"), CATALOG_TABLE);
        Assertions.assertEquals(
                "CREATE TABLE IF NOT EXISTS \"test\".\"test\" (\n"
                        + "\"test\" VARCHAR(32672) NOT NULL,\n"
                        + "\"test2\" VARCHAR(32672) NOT NULL,\n"
                        + "\"test3\" VARCHAR(32672),\n"
                        + "PRIMARY KEY ( \"test\", \"test2\" )\n"
                        + ");",
                sql);
    }

    @Test
    void testSplitSqlStatementsWithSemicolonInComment() {
        String sql =
                "CREATE TABLE IF NOT EXISTS \"QA_SINK\".\"mysql_all_type_has_key_cdcasdf\" ("
                        + "\"int_col\" INT NOT NULL"
                        + ");\n"
                        + "COMMENT ON COLUMN \"QA_SINK\".\"mysql_all_type_has_key_cdcasdf\"."
                        + "\"timestamp_col\" IS '‘单引号’,;“双引号”';";

        java.util.List<String> statements = DB2Catalog.splitSqlStatements(sql);

        Assertions.assertEquals(2, statements.size());
        Assertions.assertEquals(
                "CREATE TABLE IF NOT EXISTS \"QA_SINK\".\"mysql_all_type_has_key_cdcasdf\" ("
                        + "\"int_col\" INT NOT NULL"
                        + ")",
                statements.get(0));
        Assertions.assertEquals(
                "COMMENT ON COLUMN \"QA_SINK\".\"mysql_all_type_has_key_cdcasdf\"."
                        + "\"timestamp_col\" IS '‘单引号’,;“双引号”'",
                statements.get(1));
    }

    @Test
    void testUniqueKeyColumnsAreNotNull() {
        CatalogTable catalogTable =
                CatalogTable.of(
                        TableIdentifier.of("catalog", "database", "uk_table"),
                        TableSchema.builder()
                                .columns(
                                        Arrays.asList(
                                                PhysicalColumn.of(
                                                        "id",
                                                        BasicType.INT_TYPE,
                                                        (Long) null,
                                                        true,
                                                        null,
                                                        ""),
                                                PhysicalColumn.of(
                                                        "unique_col",
                                                        BasicType.INT_TYPE,
                                                        (Long) null,
                                                        true,
                                                        null,
                                                        "")))
                                .constraintKey(
                                        Collections.singletonList(
                                                ConstraintKey.of(
                                                        ConstraintKey.ConstraintType.UNIQUE_KEY,
                                                        "uk_unique_col",
                                                        Collections.singletonList(
                                                                ConstraintKey.ConstraintKeyColumn
                                                                        .of(
                                                                                "unique_col",
                                                                                ConstraintKey
                                                                                        .ColumnSortType
                                                                                        .ASC)))))
                                .build(),
                        Collections.emptyMap(),
                        Collections.emptyList(),
                        "comment");

        DB2CatalogFactory factory = new DB2CatalogFactory();
        DB2Catalog catalog =
                (DB2Catalog)
                        factory.createCatalog(
                                "test",
                                ReadonlyConfig.fromMap(
                                        new HashMap<String, Object>() {
                                            {
                                                put(
                                                        "base-url",
                                                        "jdbc:kingbase://localhost:5432/test");
                                                put("username", "test");
                                                put("password", "test");
                                            }
                                        }));

        String sql = catalog.getCreateTableSql(TablePath.of("test.test.uk_table"), catalogTable);

        Assertions.assertTrue(sql.contains("\"unique_col\" INT NOT NULL"));
        Assertions.assertTrue(sql.contains("CONSTRAINT uk_unique_col_"));
    }
}

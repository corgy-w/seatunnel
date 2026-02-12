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
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.common.utils.JdbcUrlUtil;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Test class for Gbase8a Catalog. This test verifies the auto-create table functionality.
 *
 * <p>NOTE: This test is disabled by default. To run it, you need to:
 *
 * <ol>
 *   <li>Have a running Gbase8a instance
 *   <li>Update the connection parameters below (URL, user, password)
 *   <li>Remove the @Disabled annotation
 * </ol>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled(
        "Please run this test in your local environment with a running Gbase8a instance. "
                + "Update the connection parameters before running.")
public class Gbase8aCatalogTest {

    // Update these connection parameters according to your Gbase8a instance
    private static final String GBASE8A_URL = "jdbc:gbase://localhost:5258/testdb";
    private static final String GBASE8A_USER = "root";
    private static final String GBASE8A_PASSWORD = "root";

    private static final String CATALOG_NAME = "gbase8a";
    private static final String DATABASE_NAME = "testdb";
    private static final String TEST_TABLE = "test_auto_create_table";
    private static final String TEST_TABLE_2 = "test_auto_create_table_2";

    private static Gbase8aCatalog catalog;
    private static TablePath sourceTablePath;
    private static CatalogTable sourceCatalogTable;

    @BeforeAll
    static void setUp() {
        JdbcUrlUtil.UrlInfo urlInfo = JdbcUrlUtil.getUrlInfo(GBASE8A_URL);
        catalog = new Gbase8aCatalog(CATALOG_NAME, GBASE8A_USER, GBASE8A_PASSWORD, urlInfo);
        catalog.open();

        sourceTablePath = TablePath.of(DATABASE_NAME, TEST_TABLE);
        sourceCatalogTable = createTestCatalogTable();
    }

    @AfterAll
    static void tearDown() {
        if (catalog != null) {
            try {
                // Clean up test tables
                if (catalog.tableExists(sourceTablePath)) {
                    catalog.dropTable(sourceTablePath, true);
                }
                TablePath tablePath2 = TablePath.of(DATABASE_NAME, TEST_TABLE_2);
                if (catalog.tableExists(tablePath2)) {
                    catalog.dropTable(tablePath2, true);
                }
            } catch (Exception e) {
                // Ignore cleanup errors
                System.err.println("Error during cleanup: " + e.getMessage());
            }
            catalog.close();
        }
    }

    @Test
    @Order(1)
    void testDatabaseExists() {
        Assertions.assertTrue(catalog.databaseExists(DATABASE_NAME), "Database should exist");
    }

    @Test
    @Order(2)
    void testListDatabases() {
        List<String> databases = catalog.listDatabases();
        Assertions.assertNotNull(databases, "Database list should not be null");
        Assertions.assertTrue(
                databases.contains(DATABASE_NAME), "Database list should contain testdb");
    }

    @Test
    @Order(3)
    void testListTables() {
        List<String> tables = catalog.listTables(DATABASE_NAME);
        Assertions.assertNotNull(tables, "Table list should not be null");
    }

    @Test
    @Order(4)
    void testCreateTable() {
        // Test creating table with auto-create functionality
        catalog.createTable(sourceTablePath, sourceCatalogTable, true);

        // Verify table was created
        Assertions.assertTrue(catalog.tableExists(sourceTablePath), "Table should be created");

        // Verify table structure
        CatalogTable createdTable = catalog.getTable(sourceTablePath);
        Assertions.assertNotNull(createdTable, "Created table should not be null");

        TableSchema schema = createdTable.getTableSchema();
        Assertions.assertNotNull(schema, "Table schema should not be null");

        List<Column> columns = schema.getColumns();
        Assertions.assertNotNull(columns, "Columns should not be null");
        Assertions.assertEquals(5, columns.size(), "Should have 5 columns");

        // Verify column names and types
        Assertions.assertEquals("id", columns.get(0).getName());
        Assertions.assertEquals("name", columns.get(1).getName());
        Assertions.assertEquals("age", columns.get(2).getName());
        Assertions.assertEquals("email", columns.get(3).getName());
        Assertions.assertEquals("created_at", columns.get(4).getName());
    }

    @Test
    @Order(5)
    void testCreateTableFromAnotherCatalogTable() {
        // Test creating a table from another catalog table (simulating data sync scenario)
        TablePath targetTablePath = TablePath.of(DATABASE_NAME, TEST_TABLE_2);

        catalog.createTable(targetTablePath, sourceCatalogTable, true);

        // Verify table was created
        Assertions.assertTrue(
                catalog.tableExists(targetTablePath), "Target table should be created");

        // Verify table structure matches source
        CatalogTable targetTable = catalog.getTable(targetTablePath);
        TableSchema sourceSchema = sourceCatalogTable.getTableSchema();
        TableSchema targetSchema = targetTable.getTableSchema();

        Assertions.assertEquals(
                sourceSchema.getColumns().size(),
                targetSchema.getColumns().size(),
                "Column count should match");

        // Clean up
        catalog.dropTable(targetTablePath, true);
    }

    @Test
    @Order(6)
    void testGetTable() {
        if (!catalog.tableExists(sourceTablePath)) {
            catalog.createTable(sourceTablePath, sourceCatalogTable, true);
        }

        CatalogTable table = catalog.getTable(sourceTablePath);
        Assertions.assertNotNull(table, "Table should not be null");
        Assertions.assertEquals(TEST_TABLE, table.getTableId().getTableName());
        Assertions.assertEquals(DATABASE_NAME, table.getTableId().getDatabaseName());
    }

    @Test
    @Order(7)
    void testDropTable() {
        // Create a temporary table to drop
        TablePath tempTablePath = TablePath.of(DATABASE_NAME, "temp_table_to_drop");
        catalog.createTable(tempTablePath, sourceCatalogTable, true);
        Assertions.assertTrue(catalog.tableExists(tempTablePath), "Temp table should exist");

        // Drop the table
        catalog.dropTable(tempTablePath, true);
        Assertions.assertFalse(catalog.tableExists(tempTablePath), "Temp table should be dropped");
    }

    @Test
    @Order(8)
    void testTableExists() {
        if (!catalog.tableExists(sourceTablePath)) {
            catalog.createTable(sourceTablePath, sourceCatalogTable, true);
        }
        Assertions.assertTrue(catalog.tableExists(sourceTablePath), "Table should exist");

        TablePath nonExistentTable = TablePath.of(DATABASE_NAME, "non_existent_table_xyz");
        Assertions.assertFalse(
                catalog.tableExists(nonExistentTable), "Non-existent table should not exist");
    }

    @Test
    @Order(9)
    void testCreateTableSql() {
        // Test the SQL generation for create table
        String createTableSql =
                catalog.getCreateTableSql(sourceTablePath, sourceCatalogTable, false);

        Assertions.assertNotNull(createTableSql, "Create table SQL should not be null");
        Assertions.assertTrue(
                createTableSql.contains("CREATE TABLE IF NOT EXISTS"),
                "SQL should contain CREATE TABLE IF NOT EXISTS");
        Assertions.assertTrue(createTableSql.contains(TEST_TABLE), "SQL should contain table name");
        Assertions.assertTrue(createTableSql.contains("id"), "SQL should contain id column");
        Assertions.assertTrue(createTableSql.contains("name"), "SQL should contain name column");

        System.out.println("Generated CREATE TABLE SQL:");
        System.out.println(createTableSql);
    }

    @Test
    @Order(10)
    void testCreateTableSqlWithStringType() {
        // Test that STRING type (without length) generates LONGTEXT instead of VARCHAR
        CatalogTable stringTable = createStringTestCatalogTable();
        String createTableSql = catalog.getCreateTableSql(sourceTablePath, stringTable, false);

        Assertions.assertNotNull(createTableSql, "Create table SQL should not be null");

        // STRING type without length should use LONGTEXT
        Assertions.assertTrue(
                createTableSql.contains("LONGTEXT"),
                "STRING type without length should generate LONGTEXT, not plain VARCHAR");

        // Make sure we don't have plain VARCHAR without length
        Assertions.assertFalse(
                createTableSql.matches("VARCHAR[^\\(]"),
                "Should not have VARCHAR without length specification");

        System.out.println("Generated CREATE TABLE SQL for STRING type:");
        System.out.println(createTableSql);
    }

    @Test
    @Order(11)
    void testCreateTableSqlWithSourceType() {
        // Test that source type is ignored and reconvert is used
        // This simulates the case where source table has VARCHAR without length
        CatalogTable tableWithSourceType = createTableWithSourceType();
        String createTableSql =
                catalog.getCreateTableSql(sourceTablePath, tableWithSourceType, false);

        Assertions.assertNotNull(createTableSql, "Create table SQL should not be null");

        // The SQL should use LONGTEXT instead of plain VARCHAR
        Assertions.assertTrue(
                createTableSql.contains("LONGTEXT") || createTableSql.contains("TEXT"),
                "Should use TEXT type when column has no length, not plain VARCHAR");

        System.out.println("Generated CREATE TABLE SQL with sourceType:");
        System.out.println(createTableSql);
    }

    /**
     * Creates a test CatalogTable with various column types to test auto-create table
     * functionality.
     */
    private static CatalogTable createTestCatalogTable() {
        List<Column> columns = new ArrayList<>();

        // Primary key column
        columns.add(
                PhysicalColumn.builder()
                        .name("id")
                        .dataType(BasicType.INT_TYPE)
                        .nullable(false)
                        .comment("Primary key id")
                        .build());

        // String columns
        columns.add(
                PhysicalColumn.builder()
                        .name("name")
                        .dataType(BasicType.STRING_TYPE)
                        .nullable(false)
                        .comment("User name")
                        .build());

        // Numeric columns
        columns.add(
                PhysicalColumn.builder()
                        .name("age")
                        .dataType(BasicType.INT_TYPE)
                        .nullable(true)
                        .comment("User age")
                        .build());

        columns.add(
                PhysicalColumn.builder()
                        .name("email")
                        .dataType(BasicType.STRING_TYPE)
                        .nullable(true)
                        .comment("User email")
                        .build());

        // Timestamp column
        columns.add(
                PhysicalColumn.builder()
                        .name("created_at")
                        .dataType(LocalTimeType.LOCAL_DATE_TIME_TYPE)
                        .nullable(true)
                        .comment("Creation timestamp")
                        .build());

        // Primary key
        PrimaryKey primaryKey = PrimaryKey.of("pk_" + TEST_TABLE, Collections.singletonList("id"));

        // Table schema
        TableSchema schema = TableSchema.builder().columns(columns).primaryKey(primaryKey).build();

        // Table identifier
        org.apache.seatunnel.api.table.catalog.TableIdentifier tableIdentifier =
                org.apache.seatunnel.api.table.catalog.TableIdentifier.of(
                        CATALOG_NAME, DATABASE_NAME, null, TEST_TABLE);

        return CatalogTable.of(
                tableIdentifier,
                schema,
                new HashMap<>(),
                Collections.emptyList(),
                "Test table for auto-create functionality");
    }

    /**
     * Creates a test CatalogTable with STRING types (no length) to verify proper type conversion.
     */
    private static CatalogTable createStringTestCatalogTable() {
        List<Column> columns = new ArrayList<>();

        columns.add(
                PhysicalColumn.builder()
                        .name("id")
                        .dataType(BasicType.INT_TYPE)
                        .nullable(false)
                        .comment("Primary key")
                        .build());

        // STRING type without length - should convert to LONGTEXT
        columns.add(
                PhysicalColumn.builder()
                        .name("description")
                        .dataType(BasicType.STRING_TYPE)
                        .nullable(true)
                        .comment("Long text description")
                        .build());

        columns.add(
                PhysicalColumn.builder()
                        .name("title")
                        .dataType(BasicType.STRING_TYPE)
                        .nullable(false)
                        .comment("Title")
                        .build());

        PrimaryKey primaryKey = PrimaryKey.of("pk_string_test", Collections.singletonList("id"));
        TableSchema schema = TableSchema.builder().columns(columns).primaryKey(primaryKey).build();

        org.apache.seatunnel.api.table.catalog.TableIdentifier tableIdentifier =
                org.apache.seatunnel.api.table.catalog.TableIdentifier.of(
                        CATALOG_NAME, DATABASE_NAME, null, "string_test_table");

        return CatalogTable.of(
                tableIdentifier,
                schema,
                new HashMap<>(),
                Collections.emptyList(),
                "Test table for STRING type verification");
    }

    /**
     * Creates a test CatalogTable with sourceType set to VARCHAR (without length) to verify that
     * reconvert is used instead of sourceType.
     */
    private static CatalogTable createTableWithSourceType() {
        List<Column> columns = new ArrayList<>();

        // Create a column with sourceType = "VARCHAR" (no length)
        // This simulates reading from a Gbase8a table where metadata returns VARCHAR without
        // length
        Column columnWithSourceType =
                PhysicalColumn.builder()
                        .name("username")
                        .dataType(BasicType.STRING_TYPE)
                        .nullable(false)
                        .comment("User name")
                        .sourceType("VARCHAR")
                        .build();

        columns.add(
                PhysicalColumn.builder()
                        .name("id")
                        .dataType(BasicType.INT_TYPE)
                        .nullable(false)
                        .comment("Primary key")
                        .build());

        columns.add(columnWithSourceType);

        PrimaryKey primaryKey =
                PrimaryKey.of("pk_sourcetype_test", Collections.singletonList("id"));
        TableSchema schema = TableSchema.builder().columns(columns).primaryKey(primaryKey).build();

        org.apache.seatunnel.api.table.catalog.TableIdentifier tableIdentifier =
                org.apache.seatunnel.api.table.catalog.TableIdentifier.of(
                        CATALOG_NAME, DATABASE_NAME, null, "sourcetype_test_table");

        return CatalogTable.of(
                tableIdentifier,
                schema,
                new HashMap<>(),
                Collections.emptyList(),
                "Test table for sourceType verification");
    }
}

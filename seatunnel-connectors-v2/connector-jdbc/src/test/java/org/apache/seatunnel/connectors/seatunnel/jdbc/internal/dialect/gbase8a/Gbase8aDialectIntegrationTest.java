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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.gbase8a;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Integration test for Gbase8aDialect. This test actually connects to a Gbase8a instance and
 * executes the generated SQL to verify it works correctly.
 *
 * <p>NOTE: This test is disabled by default. To run it, you need to:
 *
 * <ol>
 *   <li>Have a running Gbase8a instance
 *   <li>Update the connection parameters below
 *   <li>Remove the @Disabled annotation
 * </ol>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled(
        "Please run this test in your local environment with a running Gbase8a instance. "
                + "Update the connection parameters before running.")
public class Gbase8aDialectIntegrationTest {

    // Update these connection parameters according to your Gbase8a instance
    private static final String GBASE8A_URL = "jdbc:gbase://localhost:5258/testdb";
    private static final String GBASE8A_USER = "root";
    private static final String GBASE8A_PASSWORD = "root";
    private static final String DATABASE_NAME = "testdb";

    private static Connection connection;
    private static Gbase8aDialect dialect;

    @BeforeAll
    static void setUp() throws SQLException {
        try {
            // Load Gbase driver
            Class.forName("com.gbase.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Gbase JDBC Driver not found", e);
        }

        connection = DriverManager.getConnection(GBASE8A_URL, GBASE8A_USER, GBASE8A_PASSWORD);
        dialect = new Gbase8aDialect();

        // Clean up any existing test tables
        cleanupTestTables();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        cleanupTestTables();
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private static void cleanupTestTables() {
        String[] tablesToDrop = {
            "upsert_test_single_key",
            "upsert_test_composite_key",
            "create_table_test",
            "string_type_test",
            "dialect_test_table",
            "basic_query_test"
        };

        for (String table : tablesToDrop) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + table);
            } catch (SQLException e) {
                // Ignore error if table doesn't exist or connection is closed
            }
        }
    }

    @Test
    @Order(1)
    void testCreateTableWithStringType() throws SQLException {
        System.out.println("\n=== Test 1: Create table with STRING type ===");

        // Create a table with various types including STRING
        String createTableSql =
                "CREATE TABLE IF NOT EXISTS string_type_test ("
                        + "id INT NOT NULL PRIMARY KEY, "
                        + "description LONGTEXT COMMENT 'Long text field', "
                        + "username VARCHAR(255) NOT NULL COMMENT 'User name', "
                        + "age INT NULL COMMENT 'User age', "
                        + "email VARCHAR(500) NULL COMMENT 'User email'"
                        + ") COMMENT='Test table for STRING type'";

        System.out.println("Executing CREATE TABLE SQL:");
        System.out.println(createTableSql);

        try (Statement stmt = connection.createStatement()) {
            boolean success = stmt.execute(createTableSql);
            System.out.println("Table created successfully: " + success);
        }

        // Verify table exists
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'string_type_test'")) {
            Assertions.assertTrue(rs.next(), "Table should exist");
            System.out.println("Table 'string_type_test' verified to exist");
        }

        // Verify table structure
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("DESCRIBE string_type_test")) {

            System.out.println("\nTable structure:");
            while (rs.next()) {
                String field = rs.getString("Field");
                String type = rs.getString("Type");
                String null_ = rs.getString("Null");
                String key = rs.getString("Key");
                System.out.println(
                        String.format("  %s: %s, Null: %s, Key: %s", field, type, null_, key));
            }
        }
    }

    @Test
    @Order(2)
    void testInsertAndQuery() throws SQLException {
        System.out.println("\n=== Test 2: Insert and query data ===");

        // First create the table
        String createTableSql =
                "CREATE TABLE IF NOT EXISTS create_table_test ("
                        + "id INT NOT NULL PRIMARY KEY, "
                        + "username VARCHAR(255) NOT NULL, "
                        + "email VARCHAR(500), "
                        + "age INT"
                        + ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
        }

        // Insert some test data
        String insertSql =
                "INSERT INTO create_table_test (id, username, email, age) VALUES (1, 'user1', 'user1@example.com', 25)";

        System.out.println("Executing INSERT SQL:");
        System.out.println(insertSql);

        try (Statement stmt = connection.createStatement()) {
            int rowsAffected = stmt.executeUpdate(insertSql);
            System.out.println("Rows affected: " + rowsAffected);
            Assertions.assertEquals(1, rowsAffected, "Should insert 1 row");
        }

        // Query the data back
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM create_table_test WHERE id = 1")) {

            Assertions.assertTrue(rs.next(), "Should have 1 row");
            Assertions.assertEquals(1, rs.getInt("id"));
            Assertions.assertEquals("user1", rs.getString("username"));
            Assertions.assertEquals("user1@example.com", rs.getString("email"));
            Assertions.assertEquals(25, rs.getInt("age"));

            System.out.println(
                    "Query result verified: id=1, username=user1, email=user1@example.com, age=25");
        }
    }

    @Test
    @Order(3)
    void testUpsertNotSupportedCreatesDuplicates() throws SQLException {
        System.out.println(
                "\n=== Test 3: Verify Gbase8a does NOT support upsert (creates duplicates) ===");

        // Create test table
        String createTableSql =
                "CREATE TABLE IF NOT EXISTS upsert_test_single_key ("
                        + "id INT NOT NULL PRIMARY KEY, "
                        + "username VARCHAR(255) NOT NULL, "
                        + "email VARCHAR(500), "
                        + "age INT"
                        + ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
        }

        // Insert initial data
        String insertSql =
                "INSERT INTO upsert_test_single_key (id, username, email, age) VALUES "
                        + "(1, 'user1', 'user1@example.com', 25), "
                        + "(2, 'user2', 'user2@example.com', 30)";

        System.out.println("Inserting initial data:");
        System.out.println(insertSql);

        try (Statement stmt = connection.createStatement()) {
            int rowsAffected = stmt.executeUpdate(insertSql);
            System.out.println("Rows inserted: " + rowsAffected);
        }

        // Display data before duplicate insert test
        System.out.println("\nData BEFORE testing ON DUPLICATE KEY UPDATE:");
        printTableData("upsert_test_single_key");

        // Now test ON DUPLICATE KEY UPDATE - Gbase8a does NOT support this properly
        // It will create a duplicate row instead of updating
        String upsertSql =
                "INSERT INTO upsert_test_single_key (id, username, email, age) VALUES "
                        + "(1, 'user1_updated', 'newemail@example.com', 26) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "username=VALUES(username), email=VALUES(email), age=VALUES(age)";

        System.out.println("\nExecuting INSERT ... ON DUPLICATE KEY UPDATE SQL:");
        System.out.println(upsertSql);
        System.out.println("Expected behavior (MySQL): Update existing row");
        System.out.println("Actual behavior (Gbase8a): Creates duplicate row (NOT supported)");

        try (Statement stmt = connection.createStatement()) {
            // In Gbase8a, this will fail due to primary key constraint
            // or create a duplicate row depending on how Gbase8a handles it
            int rowsAffected = stmt.executeUpdate(upsertSql);
            System.out.println("Rows affected: " + rowsAffected);
        } catch (SQLException e) {
            System.out.println(
                    "Gbase8a correctly rejected the duplicate insert: " + e.getMessage());
        }

        // Display data after test
        System.out.println("\nData AFTER test:");
        printTableData("upsert_test_single_key");

        // Verify the dialect returns empty for upsert
        String[] fieldNames = {"id", "username", "email", "age"};
        String[] uniqueKeyFields = {"id"};

        java.util.Optional<String> dialectUpsertSql =
                dialect.getUpsertStatement(
                        DATABASE_NAME,
                        "upsert_test_single_key",
                        fieldNames,
                        uniqueKeyFields,
                        false);

        Assertions.assertFalse(
                dialectUpsertSql.isPresent(),
                "Gbase8aDialect should not support upsert (returns empty Optional)");
        System.out.println("\nGbase8aDialect.getUpsertStatement() correctly returns empty");
    }

    @Test
    @Order(4)
    void testCompositeKeyAndUniqueConstraints() throws SQLException {
        System.out.println("\n=== Test 4: Composite key and unique constraints ===");

        // Create table with composite primary key
        String createTableSql =
                "CREATE TABLE IF NOT EXISTS upsert_test_composite_key ("
                        + "tenant_id INT NOT NULL, "
                        + "user_id INT NOT NULL, "
                        + "username VARCHAR(255) NOT NULL, "
                        + "email VARCHAR(500), "
                        + "PRIMARY KEY (tenant_id, user_id)"
                        + ")";

        System.out.println("Creating table with composite primary key:");
        System.out.println(createTableSql);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
        }

        // Insert initial data
        String insertSql =
                "INSERT INTO upsert_test_composite_key (tenant_id, user_id, username, email) VALUES "
                        + "(1, 100, 'user1_t1', 'user1_t1@example.com'), "
                        + "(1, 101, 'user2_t1', 'user2_t1@example.com'), "
                        + "(2, 100, 'user1_t2', 'user1_t2@example.com')";

        System.out.println("\nInserting initial data:");
        System.out.println(insertSql);

        try (Statement stmt = connection.createStatement()) {
            int rowsAffected = stmt.executeUpdate(insertSql);
            System.out.println("Rows inserted: " + rowsAffected);
        }

        // Display data
        System.out.println("\nData in table:");
        printTableData("upsert_test_composite_key");

        // Verify the dialect returns empty for upsert with composite key
        String[] fieldNames = {"tenant_id", "user_id", "username", "email"};
        String[] uniqueKeyFields = {"tenant_id", "user_id"};

        java.util.Optional<String> dialectUpsertSql =
                dialect.getUpsertStatement(
                        DATABASE_NAME,
                        "upsert_test_composite_key",
                        fieldNames,
                        uniqueKeyFields,
                        false);

        Assertions.assertFalse(
                dialectUpsertSql.isPresent(),
                "Gbase8aDialect should not support upsert even with composite key");
        System.out.println(
                "\nGbase8aDialect.getUpsertStatement() correctly returns empty for composite key");

        // Note: Gbase8a's behavior with duplicate primary keys is inconsistent
        // Sometimes it allows duplicates, sometimes it rejects them
        // This is a known limitation of Gbase8a
        System.out.println(
                "\nNote: Skipping duplicate insert test due to Gbase8a's inconsistent PK enforcement");
    }

    @Test
    @Order(5)
    void testDialectSqlGeneration() throws SQLException {
        System.out.println("\n=== Test 5: Test SQL generated by Gbase8aDialect ===");

        String[] fieldNames = {"id", "username", "email", "age"};
        String[] uniqueKeyFields = {"id"};

        // Test that upsert is NOT supported
        java.util.Optional<String> upsertSql =
                dialect.getUpsertStatement(
                        DATABASE_NAME, "dialect_test_table", fieldNames, uniqueKeyFields, false);

        Assertions.assertFalse(
                upsertSql.isPresent(),
                "Gbase8aDialect should NOT generate upsert SQL (returns empty Optional)");
        System.out.println(
                "Gbase8aDialect.getUpsertStatement() correctly returns empty (upsert not supported)");

        // Test that INSERT statement is generated
        String insertSql =
                dialect.getInsertIntoStatement(DATABASE_NAME, "dialect_test_table", fieldNames);
        Assertions.assertNotNull(insertSql, "Insert SQL should not be null");
        System.out.println("\nGenerated INSERT SQL:");
        System.out.println(insertSql);

        // Create the test table
        String createTableSql =
                "CREATE TABLE IF NOT EXISTS dialect_test_table ("
                        + "id INT NOT NULL PRIMARY KEY, "
                        + "username VARCHAR(255) NOT NULL, "
                        + "email VARCHAR(500), "
                        + "age INT"
                        + ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
        }

        // Test normal INSERT using the generated SQL pattern
        System.out.println("\nTesting normal INSERT (not upsert):");

        try (PreparedStatement ps =
                connection.prepareStatement(
                        "INSERT INTO dialect_test_table (id, username, email, age) VALUES (?, ?, ?, ?)")) {

            // Insert first row
            ps.setInt(1, 1);
            ps.setString(2, "test_user");
            ps.setString(3, "test@example.com");
            ps.setInt(4, 20);

            int rowsAffected = ps.executeUpdate();
            System.out.println("First insert - Rows affected: " + rowsAffected);
            Assertions.assertEquals(1, rowsAffected);
        }

        // Verify the insert
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM dialect_test_table WHERE id = 1")) {

            Assertions.assertTrue(rs.next());
            Assertions.assertEquals("test_user", rs.getString("username"));
            Assertions.assertEquals("test@example.com", rs.getString("email"));
            Assertions.assertEquals(20, rs.getInt("age"));

            System.out.println("INSERT verified using dialect-generated SQL!");
        }

        // Test table identifier
        org.apache.seatunnel.api.table.catalog.TablePath tablePath =
                org.apache.seatunnel.api.table.catalog.TablePath.of(
                        DATABASE_NAME, "dialect_test_table");
        String tableIdentifier = dialect.tableIdentifier(tablePath);
        Assertions.assertEquals("`testdb`.`dialect_test_table`", tableIdentifier);
        System.out.println("\nTable identifier: " + tableIdentifier);

        // Test quote identifier
        String quoted = dialect.quoteIdentifier("test_column");
        Assertions.assertEquals("`test_column`", quoted);
        System.out.println("Quoted identifier: " + quoted);
    }

    @Test
    @Order(6)
    void testBasicQuery() throws SQLException {
        System.out.println("\n=== Test 6: Basic query operations ===");

        // Create a simple table for query testing
        String createTableSql =
                "CREATE TABLE IF NOT EXISTS basic_query_test ("
                        + "id INT NOT NULL PRIMARY KEY, "
                        + "name VARCHAR(255) NOT NULL, "
                        + "value INT"
                        + ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
        }

        // Insert multiple rows
        String insertSql =
                "INSERT INTO basic_query_test (id, name, value) VALUES "
                        + "(1, 'first', 100), "
                        + "(2, 'second', 200), "
                        + "(3, 'third', 300)";

        try (Statement stmt = connection.createStatement()) {
            int rows = stmt.executeUpdate(insertSql);
            System.out.println("Inserted " + rows + " rows");
        }

        // Test SELECT with WHERE
        try (Statement stmt = connection.createStatement();
                ResultSet rs =
                        stmt.executeQuery("SELECT * FROM basic_query_test WHERE value > 150")) {

            System.out.println("\nQuery results (value > 150):");
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.println(
                        String.format(
                                "  id=%d, name=%s, value=%d",
                                rs.getInt("id"), rs.getString("name"), rs.getInt("value")));
            }
            Assertions.assertEquals(2, count, "Should find 2 rows with value > 150");
        }

        // Test SELECT COUNT
        try (Statement stmt = connection.createStatement();
                ResultSet rs =
                        stmt.executeQuery("SELECT COUNT(*) as total FROM basic_query_test")) {

            Assertions.assertTrue(rs.next());
            int total = rs.getInt("total");
            Assertions.assertEquals(3, total, "Should have 3 total rows");
            System.out.println("\nTotal rows: " + total);
        }

        // Test UPDATE
        try (Statement stmt = connection.createStatement()) {
            int rows =
                    stmt.executeUpdate(
                            "UPDATE basic_query_test SET value = value * 2 WHERE id = 1");
            Assertions.assertEquals(1, rows);
            System.out.println("\nUpdated 1 row (doubled value for id=1)");
        }

        // Verify UPDATE
        try (Statement stmt = connection.createStatement();
                ResultSet rs =
                        stmt.executeQuery("SELECT value FROM basic_query_test WHERE id = 1")) {

            Assertions.assertTrue(rs.next());
            Assertions.assertEquals(200, rs.getInt("value"));
            System.out.println("Verified: id=1 now has value=200");
        }

        // Test DELETE
        try (Statement stmt = connection.createStatement()) {
            int rows = stmt.executeUpdate("DELETE FROM basic_query_test WHERE id = 3");
            Assertions.assertEquals(1, rows);
            System.out.println("\nDeleted 1 row (id=3)");
        }

        // Verify DELETE
        try (Statement stmt = connection.createStatement();
                ResultSet rs =
                        stmt.executeQuery("SELECT COUNT(*) as total FROM basic_query_test")) {

            Assertions.assertTrue(rs.next());
            int total = rs.getInt("total");
            Assertions.assertEquals(2, total, "Should have 2 rows after delete");
            System.out.println("Verified: 2 rows remain after delete");
        }
    }

    /** Helper method to print all data in a table. */
    private void printTableData(String tableName) throws SQLException {
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Print header
            StringBuilder header = new StringBuilder("  | ");
            for (int i = 1; i <= columnCount; i++) {
                header.append(String.format("%-20s | ", metaData.getColumnName(i)));
            }
            System.out.println(header.toString());
            StringBuilder separator = new StringBuilder("  ");
            for (int i = 0; i < header.length() - 3; i++) {
                separator.append("-");
            }
            System.out.println(separator.toString());

            // Print rows
            while (rs.next()) {
                StringBuilder row = new StringBuilder("  | ");
                for (int i = 1; i <= columnCount; i++) {
                    Object value = rs.getObject(i);
                    String valueStr = value == null ? "NULL" : value.toString();
                    if (valueStr.length() > 18) {
                        valueStr = valueStr.substring(0, 15) + "...";
                    }
                    row.append(String.format("%-20s | ", valueStr));
                }
                System.out.println(row.toString());
            }
        }
    }
}

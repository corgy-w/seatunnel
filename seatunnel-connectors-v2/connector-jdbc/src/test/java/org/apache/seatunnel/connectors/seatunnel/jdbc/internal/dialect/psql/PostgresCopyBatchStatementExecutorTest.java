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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.psql;

import org.apache.seatunnel.shade.org.apache.commons.csv.CSVPrinter;

import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

public class PostgresCopyBatchStatementExecutorTest {

    /*
     * Test CSV escaping for JSON string containing double quotes.
     * This is the primary use case that triggered the fix.
     * Input: ["创业板业务控制标志","创业板业务-注册制"]
     * Expected: Inner quotes are doubled per CSV spec, field is wrapped with quotes.
     */
    @Test
    public void testCsvEscapingForJsonString() throws Exception {
        String value = "[\"创业板业务控制标志\",\"创业板业务-注册制\"]";
        String out = renderSingleStringColumn(value);
        Assertions.assertEquals("\"[\"\"创业板业务控制标志\"\",\"\"创业板业务-注册制\"\"]\"\n", out);
    }

    /*
     * Test CSV escaping when value itself contains outer quotes.
     * Input: "zhangsan"
     * Expected: Each quote is doubled, field is wrapped with quotes.
     */
    @Test
    public void testCsvEscapingForQuotedStringValue() throws Exception {
        String out = renderSingleStringColumn("\"zhangsan\"");
        Assertions.assertEquals("\"\"\"zhangsan\"\"\"\n", out);
    }

    /*
     * Test CSV escaping for string containing comma.
     * Comma triggers field quoting per CSV spec.
     */
    @Test
    public void testCsvEscapingForComma() throws Exception {
        String out = renderSingleStringColumn("a,b");
        Assertions.assertEquals("\"a,b\"\n", out);
    }

    /*
     * Test that null byte (\u0000) is removed from string.
     * PostgreSQL does not support null byte in text fields.
     */
    @Test
    public void testNullByteRemovedInString() throws Exception {
        String out = renderSingleStringColumn("a\u0000b");
        Assertions.assertEquals("\"ab\"\n", out);
    }

    /*
     * Test simple string without special characters.
     * CSVFormat.POSTGRESQL_CSV wraps all fields with quotes.
     */
    @Test
    public void testSimpleString() throws Exception {
        String out = renderSingleStringColumn("simple");
        Assertions.assertEquals("\"simple\"\n", out);
    }

    /*
     * Test empty string handling.
     * Empty string is rendered as empty quoted field.
     */
    @Test
    public void testEmptyString() throws Exception {
        String out = renderSingleStringColumn("");
        Assertions.assertEquals("\"\"\n", out);
    }

    /*
     * Test string with only quotes.
     */
    @Test
    public void testOnlyQuotes() throws Exception {
        String out = renderSingleStringColumn("\"\"");
        Assertions.assertEquals("\"\"\"\"\"\"\n", out);
    }

    /*
     * Test string with newline character.
     * Newline triggers field quoting per CSV spec.
     */
    @Test
    public void testStringWithNewline() throws Exception {
        String out = renderSingleStringColumn("line1\nline2");
        Assertions.assertEquals("\"line1\nline2\"\n", out);
    }

    /*
     * Test string with tab character.
     * CSVFormat.POSTGRESQL_CSV wraps all fields with quotes.
     */
    @Test
    public void testStringWithTab() throws Exception {
        String out = renderSingleStringColumn("col1\tcol2");
        Assertions.assertEquals("\"col1\tcol2\"\n", out);
    }

    /*
     * Test string with backslash.
     * Backslash should not require special escaping in CSV format.
     * CSVFormat.POSTGRESQL_CSV wraps all fields with quotes.
     */
    @Test
    public void testStringWithBackslash() throws Exception {
        String out = renderSingleStringColumn("path\\to\\file");
        Assertions.assertEquals("\"path\\to\\file\"\n", out);
    }

    /*
     * Test string with multiple null bytes.
     * CSVFormat.POSTGRESQL_CSV wraps all fields with quotes.
     */
    @Test
    public void testMultipleNullBytes() throws Exception {
        String out = renderSingleStringColumn("a\u0000b\u0000c");
        Assertions.assertEquals("\"abc\"\n", out);
    }

    /*
     * Test null value handling.
     */
    @Test
    public void testNullValue() throws Exception {
        PostgresCopyBatchStatementExecutor executor = new PostgresCopyBatchStatementExecutor();
        executor.init(TablePath.of("db", "public", "t"), buildSingleStringSchema());
        executor.prepareStatements(null);
        executor.addToBatch(new SeaTunnelRow(new Object[] {null}));
        executor.csvPrinter.flush();
        String out = normalizeLineEndings(executor.csvPrinter.getOut().toString());
        Assertions.assertEquals("\n", out);
    }

    /*
     * Test multi-field row with mixed special characters.
     * This verifies that each field is independently escaped.
     * CSVFormat.POSTGRESQL_CSV wraps all fields with quotes.
     */
    @Test
    public void testMultiFieldRow() throws Exception {
        PostgresCopyBatchStatementExecutor executor = new PostgresCopyBatchStatementExecutor();
        executor.init(TablePath.of("db", "public", "t"), buildMultiColumnSchema());
        executor.prepareStatements(null);

        SeaTunnelRow row =
                new SeaTunnelRow(new Object[] {1, "simple", "with,comma", "with\"quote", null});

        executor.addToBatch(row);
        executor.csvPrinter.flush();
        String out = normalizeLineEndings(executor.csvPrinter.getOut().toString());

        Assertions.assertEquals("\"1\",\"simple\",\"with,comma\",\"with\"\"quote\",\n", out);
    }

    /*
     * Test complex real-world scenario with JSON-like data in multiple fields.
     * CSVFormat.POSTGRESQL_CSV wraps all fields with quotes.
     */
    @Test
    public void testComplexJsonLikeData() throws Exception {
        PostgresCopyBatchStatementExecutor executor = new PostgresCopyBatchStatementExecutor();
        executor.init(TablePath.of("db", "public", "t"), buildMultiColumnSchema());
        executor.prepareStatements(null);

        SeaTunnelRow row =
                new SeaTunnelRow(
                        new Object[] {
                            100,
                            "{\"key\":\"value\"}",
                            "[\"item1\",\"item2\"]",
                            "normal,text",
                            "simple"
                        });

        executor.addToBatch(row);
        executor.csvPrinter.flush();
        String out = normalizeLineEndings(executor.csvPrinter.getOut().toString());

        Assertions.assertEquals(
                "\"100\",\"{\"\"key\"\":\"\"value\"\"}\",\"[\"\"item1\"\",\"\"item2\"\"]\",\"normal,text\",\"simple\"\n",
                out);
    }

    @Test
    public void testShouldNotFailFastWhenStringCannotCastToBigint() throws Exception {
        TableSchema schema =
                new TableSchema(
                        java.util.Collections.singletonList(
                                new PhysicalColumn(
                                        "t2", BasicType.LONG_TYPE, null, null, true, null, null)),
                        null,
                        null);
        PostgresCopyBatchStatementExecutor executor = new PostgresCopyBatchStatementExecutor();
        executor.init(TablePath.of("db", "public", "t"), schema);
        executor.prepareStatements(null);

        executor.addToBatch(new SeaTunnelRow(new Object[] {"AA"}));
        executor.csvPrinter.flush();
        String out = normalizeLineEndings(executor.csvPrinter.getOut().toString());
        Assertions.assertEquals("\"AA\"\n", out);
    }

    @Test
    public void testShouldNotFailFastWhenBlankStringCannotCastToBigint() throws Exception {
        TableSchema schema =
                new TableSchema(
                        java.util.Collections.singletonList(
                                new PhysicalColumn(
                                        "t2", BasicType.LONG_TYPE, null, null, true, null, null)),
                        null,
                        null);
        PostgresCopyBatchStatementExecutor executor = new PostgresCopyBatchStatementExecutor();
        executor.init(TablePath.of("db", "public", "t"), schema);
        executor.prepareStatements(null);

        executor.addToBatch(new SeaTunnelRow(new Object[] {"  "}));
        executor.csvPrinter.flush();
        String out = normalizeLineEndings(executor.csvPrinter.getOut().toString());
        Assertions.assertEquals("\"  \"\n", out);
    }

    @Test
    public void testPreserveOriginalStringWhenCopyNumericString() throws Exception {
        TableSchema schema =
                new TableSchema(
                        java.util.Collections.singletonList(
                                new PhysicalColumn(
                                        "t2", BasicType.LONG_TYPE, null, null, true, null, null)),
                        null,
                        null);
        PostgresCopyBatchStatementExecutor executor = new PostgresCopyBatchStatementExecutor();
        executor.init(TablePath.of("db", "public", "t"), schema);
        executor.prepareStatements(null);

        executor.addToBatch(new SeaTunnelRow(new Object[] {"  12  "}));
        executor.csvPrinter.flush();
        String out = normalizeLineEndings(executor.csvPrinter.getOut().toString());
        Assertions.assertEquals("\"  12  \"\n", out);
    }

    @Test
    public void testPreserveDoubleStringWithoutScientificNotation() throws Exception {
        TableSchema schema =
                TableSchema.builder()
                        .column(
                                new PhysicalColumn(
                                        "d", BasicType.DOUBLE_TYPE, null, null, true, null, null))
                        .build();
        PostgresCopyBatchStatementExecutor executor = new PostgresCopyBatchStatementExecutor();
        executor.init(TablePath.of("db", "public", "t"), schema);
        executor.prepareStatements(null);

        executor.addToBatch(new SeaTunnelRow(new Object[] {"0.0000001"}));
        executor.csvPrinter.flush();
        String out = normalizeLineEndings(executor.csvPrinter.getOut().toString());

        // validate that we keep the original representation, not Java's Double#toString formatting
        Assertions.assertEquals("\"0.0000001\"\n", out);
    }

    @Test
    public void testBooleanStringAcceptedByPostgres() throws Exception {
        TableSchema schema =
                TableSchema.builder()
                        .column(
                                new PhysicalColumn(
                                        "b1", BasicType.BOOLEAN_TYPE, null, null, true, null, null))
                        .column(
                                new PhysicalColumn(
                                        "b2", BasicType.BOOLEAN_TYPE, null, null, true, null, null))
                        .build();
        PostgresCopyBatchStatementExecutor executor = new PostgresCopyBatchStatementExecutor();
        executor.init(TablePath.of("db", "public", "t"), schema);
        executor.prepareStatements(null);

        executor.addToBatch(new SeaTunnelRow(new Object[] {"  yes  ", "off"}));
        executor.csvPrinter.flush();
        String out = normalizeLineEndings(executor.csvPrinter.getOut().toString());

        Assertions.assertEquals("\"  yes  \",\"off\"\n", out);
    }

    @Test
    public void testShouldNotFailFastWhenStringCannotCastToBoolean() throws Exception {
        TableSchema schema =
                TableSchema.builder()
                        .column(
                                new PhysicalColumn(
                                        "b", BasicType.BOOLEAN_TYPE, null, null, true, null, null))
                        .build();
        PostgresCopyBatchStatementExecutor executor = new PostgresCopyBatchStatementExecutor();
        executor.init(TablePath.of("db", "public", "t"), schema);
        executor.prepareStatements(null);

        executor.addToBatch(new SeaTunnelRow(new Object[] {"maybe"}));
        executor.csvPrinter.flush();
        String out = normalizeLineEndings(executor.csvPrinter.getOut().toString());
        Assertions.assertEquals("\"maybe\"\n", out);
    }

    /*
     * Test batch with multiple rows to ensure CSV consistency.
     * CSVFormat.POSTGRESQL_CSV wraps all fields with quotes.
     */
    @Test
    public void testMultipleRows() throws Exception {
        PostgresCopyBatchStatementExecutor executor = new PostgresCopyBatchStatementExecutor();
        executor.init(TablePath.of("db", "public", "t"), buildSingleStringSchema());
        executor.prepareStatements(null);

        executor.addToBatch(new SeaTunnelRow(new Object[] {"row1"}));
        executor.addToBatch(new SeaTunnelRow(new Object[] {"row,2"}));
        executor.addToBatch(new SeaTunnelRow(new Object[] {"row\"3"}));
        executor.addToBatch(new SeaTunnelRow(new Object[] {null}));

        executor.csvPrinter.flush();
        String out = normalizeLineEndings(executor.csvPrinter.getOut().toString());

        Assertions.assertEquals("\"row1\"\n\"row,2\"\n\"row\"\"3\"\n\n", out);
    }

    /*
     * Test that the fix prevents double escaping.
     * Before fix: quotes were manually escaped then escaped again by CSVPrinter.
     * After fix: only CSVPrinter performs escaping, resulting in correct output.
     */
    @Test
    public void testNoDoubleEscaping() throws Exception {
        String value = "a\"b";
        String out = renderSingleStringColumn(value);

        // Correct: a"b becomes "a""b" (one level of escaping)
        Assertions.assertEquals("\"a\"\"b\"\n", out);

        // If double escaping occurred, it would be: "a""""b" (wrong!)
        Assertions.assertNotEquals("\"a\"\"\"\"b\"\n", out);
    }

    /**
     * Validates that a COPY failure does not clear the buffered CSV payload.
     *
     * <p>This protects the upper-level retry in {@code JdbcOutputFormat.flush()} from "succeeding"
     * with an empty payload (0 rows written), which would otherwise lead to silent data loss while
     * the job can still end in FINISHED.
     */
    @Test
    public void testExecuteBatchShouldNotDropBufferWhenCopyFails() throws Exception {
        CopyManager copyManager = Mockito.mock(CopyManager.class);
        PGConnection pgConnection = Mockito.mock(PGConnection.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(connection.unwrap(PGConnection.class)).thenReturn(pgConnection);
        Mockito.when(pgConnection.getCopyAPI()).thenReturn(copyManager);

        AtomicReference<String> firstCopyPayload = new AtomicReference<>();
        AtomicReference<String> secondCopyPayload = new AtomicReference<>();

        // Simulate a retriable flush flow:
        // 1) First COPY fails with a data error (e.g. 22P02) and must NOT clear the buffered CSV.
        // 2) Second COPY is invoked by the retry mechanism and must receive the SAME payload.
        Mockito.when(copyManager.copyIn(Mockito.anyString(), Mockito.any(Reader.class)))
                .thenAnswer(
                        invocation -> {
                            firstCopyPayload.set(readAll(invocation.getArgument(1)));
                            throw new SQLException("invalid input syntax", "22P02");
                        })
                .thenAnswer(
                        invocation -> {
                            secondCopyPayload.set(readAll(invocation.getArgument(1)));
                            return 1L;
                        });

        PostgresCopyBatchStatementExecutor executor = new PostgresCopyBatchStatementExecutor();
        executor.init(TablePath.of("db", "public", "t"), buildSingleStringSchema());
        executor.prepareStatements(connection);
        executor.addToBatch(new SeaTunnelRow(new Object[] {"AA"}));

        // First try: COPY fails, executor must keep the buffer so that upper-level retry can
        // resend.
        Assertions.assertThrows(JdbcConnectorException.class, executor::executeBatch);
        Assertions.assertFalse(executor.isFlushed());

        // Second try: COPY succeeds, payload should be identical to the first try (no silent drop).
        executor.executeBatch();
        Assertions.assertTrue(executor.isFlushed());

        Assertions.assertEquals(firstCopyPayload.get(), secondCopyPayload.get());
        Assertions.assertTrue(secondCopyPayload.get().contains("AA"));
    }

    @Test
    public void testExecuteBatchShouldResetBufferWhenCloseOldPrinterFails() throws Exception {
        CopyManager copyManager = Mockito.mock(CopyManager.class);
        PGConnection pgConnection = Mockito.mock(PGConnection.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(connection.unwrap(PGConnection.class)).thenReturn(pgConnection);
        Mockito.when(pgConnection.getCopyAPI()).thenReturn(copyManager);

        AtomicReference<String> firstCopyPayload = new AtomicReference<>();
        AtomicReference<String> secondCopyPayload = new AtomicReference<>();

        Mockito.when(copyManager.copyIn(Mockito.anyString(), Mockito.any(Reader.class)))
                .thenAnswer(
                        invocation -> {
                            firstCopyPayload.set(readAll(invocation.getArgument(1)));
                            return 1L;
                        })
                .thenAnswer(
                        invocation -> {
                            secondCopyPayload.set(readAll(invocation.getArgument(1)));
                            return 1L;
                        });

        PostgresCopyBatchStatementExecutor executor = new PostgresCopyBatchStatementExecutor();
        executor.init(TablePath.of("db", "public", "t"), buildSingleStringSchema());
        executor.prepareStatements(connection);

        executor.csvPrinter = new CSVPrinter(new FailingCloseableAppendable(), executor.csvFormat);

        executor.addToBatch(new SeaTunnelRow(new Object[] {"first"}));
        executor.executeBatch();
        Assertions.assertTrue(executor.isFlushed());

        executor.addToBatch(new SeaTunnelRow(new Object[] {"second"}));
        executor.executeBatch();
        Assertions.assertTrue(executor.isFlushed());

        Assertions.assertEquals("\"first\"\n", firstCopyPayload.get());
        Assertions.assertEquals("\"second\"\n", secondCopyPayload.get());
    }

    private String renderSingleStringColumn(String value) throws Exception {
        PostgresCopyBatchStatementExecutor executor = new PostgresCopyBatchStatementExecutor();
        executor.init(TablePath.of("db", "public", "t"), buildSingleStringSchema());
        executor.prepareStatements(null);
        executor.addToBatch(new SeaTunnelRow(new Object[] {value}));
        executor.csvPrinter.flush();
        return normalizeLineEndings(executor.csvPrinter.getOut().toString());
    }

    private TableSchema buildSingleStringSchema() {
        return TableSchema.builder()
                .column(
                        new PhysicalColumn(
                                "name", BasicType.STRING_TYPE, null, null, true, null, null))
                .build();
    }

    private TableSchema buildMultiColumnSchema() {
        return TableSchema.builder()
                .column(new PhysicalColumn("id", BasicType.INT_TYPE, null, null, true, null, null))
                .column(
                        new PhysicalColumn(
                                "col1", BasicType.STRING_TYPE, null, null, true, null, null))
                .column(
                        new PhysicalColumn(
                                "col2", BasicType.STRING_TYPE, null, null, true, null, null))
                .column(
                        new PhysicalColumn(
                                "col3", BasicType.STRING_TYPE, null, null, true, null, null))
                .column(
                        new PhysicalColumn(
                                "col4", BasicType.STRING_TYPE, null, null, true, null, null))
                .build();
    }

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n");
    }

    /** Reads the full content from a {@link Reader} and normalizes line endings for assertions. */
    private String readAll(Reader reader) throws Exception {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[1024];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return normalizeLineEndings(sb.toString());
    }

    private static final class FailingCloseableAppendable implements Appendable, Closeable {
        private final StringBuilder delegate = new StringBuilder();
        private boolean failOnFirstClose = true;

        @Override
        public Appendable append(CharSequence csq) {
            delegate.append(csq);
            return this;
        }

        @Override
        public Appendable append(CharSequence csq, int start, int end) {
            delegate.append(csq, start, end);
            return this;
        }

        @Override
        public Appendable append(char c) {
            delegate.append(c);
            return this;
        }

        @Override
        public void close() throws IOException {
            if (failOnFirstClose) {
                failOnFirstClose = false;
                throw new IOException("mock close failure");
            }
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}

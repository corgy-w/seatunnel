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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal;

import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.connectors.seatunnel.jdbc.config.JdbcConnectionConfig;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.connection.JdbcConnectionProvider;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.executor.BufferReducedBatchStatementExecutor;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.executor.BufferedBatchStatementExecutor;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.executor.CopyBatchStatementExecutor;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.executor.JdbcBatchStatementExecutor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

class JdbcOutputFormatReconnectSafetyTest {

    @Test
    void testFlushRetryShouldFailFastBeforeReconnectWhenCopyPayloadBuffered() throws Exception {
        JdbcConnectionProvider provider = Mockito.mock(JdbcConnectionProvider.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(provider.getOrEstablishConnection()).thenReturn(connection);
        Mockito.when(provider.getConnection()).thenReturn(connection);
        Mockito.when(provider.isConnectionValid()).thenReturn(true, true);

        TestCopyExecutor executor = new TestCopyExecutor();
        executor.flushFailure = new SQLException("connection dropped", "08006");

        JdbcOutputFormat<SeaTunnelRow, TestCopyExecutor> outputFormat =
                new JdbcOutputFormat<>(provider, buildConnectionConfig(), () -> executor);
        outputFormat.open();
        outputFormat.writeRecord(new SeaTunnelRow(new Object[] {"AA"}));

        JdbcConnectorException exception =
                Assertions.assertThrows(JdbcConnectorException.class, outputFormat::flush);
        Assertions.assertTrue(
                exception.getMessage().contains("Unsafe JDBC executor recovery is blocked"));
        Assertions.assertEquals(1, executor.executeBatchCalls);
        Assertions.assertEquals(0, executor.closeStatementsCalls);
        Mockito.verify(provider, Mockito.never()).reestablishConnection();
    }

    @Test
    void testTestOnBorrowReconnectShouldFailFastWhenCopyPayloadBuffered() throws Exception {
        JdbcConnectionProvider provider = Mockito.mock(JdbcConnectionProvider.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(provider.getOrEstablishConnection()).thenReturn(connection);
        Mockito.when(provider.getConnection()).thenReturn(connection);
        Mockito.when(provider.isConnectionValid()).thenReturn(false);

        TestCopyExecutor executor = new TestCopyExecutor();

        JdbcOutputFormat<SeaTunnelRow, TestCopyExecutor> outputFormat =
                new JdbcOutputFormat<>(provider, buildConnectionConfig(), () -> executor);
        outputFormat.open();
        outputFormat.writeRecord(new SeaTunnelRow(new Object[] {"AA"}));

        JdbcConnectorException exception =
                Assertions.assertThrows(JdbcConnectorException.class, outputFormat::flush);
        Assertions.assertTrue(
                exception.getMessage().contains("Reestablish JDBC connection failed"));
        Assertions.assertNotNull(exception.getCause());
        Assertions.assertTrue(
                exception
                        .getCause()
                        .getMessage()
                        .contains("Unsafe JDBC executor recovery is blocked"));
        Assertions.assertEquals(0, executor.executeBatchCalls);
        Assertions.assertEquals(0, executor.closeStatementsCalls);
        Mockito.verify(provider, Mockito.never()).reestablishConnection();
    }

    @Test
    void testFlushRetryShouldFailFastBeforeStatementRecoveryWhenCopyPayloadBuffered()
            throws Exception {
        JdbcConnectionProvider provider = Mockito.mock(JdbcConnectionProvider.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(provider.getOrEstablishConnection()).thenReturn(connection);
        Mockito.when(provider.getConnection()).thenReturn(connection);
        Mockito.when(provider.isConnectionValid()).thenReturn(true, true);

        TestCopyExecutor executor = new TestCopyExecutor();
        executor.flushFailure = new SQLException("No operations allowed after statement closed.");

        JdbcOutputFormat<SeaTunnelRow, TestCopyExecutor> outputFormat =
                new JdbcOutputFormat<>(provider, buildConnectionConfig(), () -> executor);
        outputFormat.open();
        outputFormat.writeRecord(new SeaTunnelRow(new Object[] {"AA"}));

        JdbcConnectorException exception =
                Assertions.assertThrows(JdbcConnectorException.class, outputFormat::flush);
        Assertions.assertTrue(
                exception.getMessage().contains("Unsafe JDBC executor recovery is blocked"));
        Assertions.assertEquals(1, executor.executeBatchCalls);
        Assertions.assertEquals(0, executor.closeStatementsCalls);
        Mockito.verify(provider, Mockito.never()).reestablishConnection();
    }

    @Test
    void testFlushRetryShouldReprepareStatementsWhenStatementIsClosedButConnectionStillValid()
            throws Exception {
        JdbcConnectionProvider provider = Mockito.mock(JdbcConnectionProvider.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(provider.getOrEstablishConnection()).thenReturn(connection);
        Mockito.when(provider.getConnection()).thenReturn(connection);
        Mockito.when(provider.isConnectionValid()).thenReturn(true, true);

        TestJdbcBatchExecutor executor = new TestJdbcBatchExecutor();

        NoSleepJdbcOutputFormat<SeaTunnelRow, TestJdbcBatchExecutor> outputFormat =
                new NoSleepJdbcOutputFormat<>(provider, buildConnectionConfig(), () -> executor);
        outputFormat.open();
        outputFormat.writeRecord(new SeaTunnelRow(new Object[] {"AA"}));

        outputFormat.flush();

        Assertions.assertEquals(2, executor.prepareStatementsCalls);
        Assertions.assertEquals(2, executor.executeBatchCalls);
        Assertions.assertEquals(1, executor.closeStatementsCalls);
        Mockito.verify(provider, Mockito.never()).reestablishConnection();
    }

    @Test
    void testFlushRetryShouldReprepareStatementsWhenSqlServerStatementHandleIsNotExecuting()
            throws Exception {
        JdbcConnectionProvider provider = Mockito.mock(JdbcConnectionProvider.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(provider.getOrEstablishConnection()).thenReturn(connection);
        Mockito.when(provider.getConnection()).thenReturn(connection);
        Mockito.when(provider.isConnectionValid()).thenReturn(true, true);

        TestJdbcBatchExecutor executor =
                new TestJdbcBatchExecutor("Statement handle is not executing.");

        NoSleepJdbcOutputFormat<SeaTunnelRow, TestJdbcBatchExecutor> outputFormat =
                new NoSleepJdbcOutputFormat<>(provider, buildConnectionConfig(), () -> executor);
        outputFormat.open();
        outputFormat.writeRecord(new SeaTunnelRow(new Object[] {"AA"}));

        outputFormat.flush();

        Assertions.assertEquals(2, executor.prepareStatementsCalls);
        Assertions.assertEquals(2, executor.executeBatchCalls);
        Assertions.assertEquals(1, executor.closeStatementsCalls);
        Mockito.verify(provider, Mockito.never()).reestablishConnection();
    }

    @Test
    void testFlushRetryShouldReconnectWhenStatementIsClosedAndConnectionIsInvalid()
            throws Exception {
        JdbcConnectionProvider provider = Mockito.mock(JdbcConnectionProvider.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(provider.getOrEstablishConnection()).thenReturn(connection);
        Mockito.when(provider.getConnection()).thenReturn(connection);
        Mockito.when(provider.isConnectionValid()).thenReturn(true, false);
        Mockito.when(provider.reestablishConnection()).thenReturn(connection);

        TestJdbcBatchExecutor executor = new TestJdbcBatchExecutor();

        NoSleepJdbcOutputFormat<SeaTunnelRow, TestJdbcBatchExecutor> outputFormat =
                new NoSleepJdbcOutputFormat<>(provider, buildConnectionConfig(), () -> executor);
        outputFormat.open();
        outputFormat.writeRecord(new SeaTunnelRow(new Object[] {"AA"}));

        outputFormat.flush();

        Assertions.assertEquals(2, executor.prepareStatementsCalls);
        Assertions.assertEquals(2, executor.executeBatchCalls);
        Assertions.assertEquals(1, executor.closeStatementsCalls);
        Mockito.verify(provider).reestablishConnection();
    }

    @Test
    void testFlushRetryShouldReconnectWhenSqlExceptionWrapsSocketClosedIOException()
            throws Exception {
        JdbcConnectionProvider provider = Mockito.mock(JdbcConnectionProvider.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(provider.getOrEstablishConnection()).thenReturn(connection);
        Mockito.when(provider.getConnection()).thenReturn(connection);
        Mockito.when(provider.isConnectionValid()).thenReturn(true, true);
        Mockito.when(provider.reestablishConnection()).thenReturn(connection);

        TestConnectionErrorExecutor executor = new TestConnectionErrorExecutor();

        NoSleepJdbcOutputFormat<SeaTunnelRow, TestConnectionErrorExecutor> outputFormat =
                new NoSleepJdbcOutputFormat<>(provider, buildConnectionConfig(), () -> executor);
        outputFormat.open();
        outputFormat.writeRecord(new SeaTunnelRow(new Object[] {"AA"}));

        outputFormat.flush();

        Assertions.assertEquals(2, executor.prepareStatementsCalls);
        Assertions.assertEquals(2, executor.executeBatchCalls);
        Assertions.assertEquals(1, executor.closeStatementsCalls);
        Mockito.verify(provider).reestablishConnection();
    }

    @Test
    void testFlushRetryShouldReconnectWhenInnerSqlCauseHasConnectionSqlState() throws Exception {
        JdbcConnectionProvider provider = Mockito.mock(JdbcConnectionProvider.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(provider.getOrEstablishConnection()).thenReturn(connection);
        Mockito.when(provider.getConnection()).thenReturn(connection);
        Mockito.when(provider.isConnectionValid()).thenReturn(true, true);
        Mockito.when(provider.reestablishConnection()).thenReturn(connection);

        TestWrappedConnectionErrorExecutor executor = new TestWrappedConnectionErrorExecutor();

        NoSleepJdbcOutputFormat<SeaTunnelRow, TestWrappedConnectionErrorExecutor> outputFormat =
                new NoSleepJdbcOutputFormat<>(provider, buildConnectionConfig(), () -> executor);
        outputFormat.open();
        outputFormat.writeRecord(new SeaTunnelRow(new Object[] {"AA"}));

        outputFormat.flush();

        Assertions.assertEquals(2, executor.prepareStatementsCalls);
        Assertions.assertEquals(2, executor.executeBatchCalls);
        Assertions.assertEquals(1, executor.closeStatementsCalls);
        Mockito.verify(provider).reestablishConnection();
    }

    @Test
    void testFlushRetryShouldReprepareStatementsWhenBatchNextExceptionIsStatementClosed()
            throws Exception {
        JdbcConnectionProvider provider = Mockito.mock(JdbcConnectionProvider.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(provider.getOrEstablishConnection()).thenReturn(connection);
        Mockito.when(provider.getConnection()).thenReturn(connection);
        Mockito.when(provider.isConnectionValid()).thenReturn(true, true);

        TestBatchNextStatementClosedExecutor executor = new TestBatchNextStatementClosedExecutor();

        NoSleepJdbcOutputFormat<SeaTunnelRow, TestBatchNextStatementClosedExecutor> outputFormat =
                new NoSleepJdbcOutputFormat<>(provider, buildConnectionConfig(), () -> executor);
        outputFormat.open();
        outputFormat.writeRecord(new SeaTunnelRow(new Object[] {"AA"}));

        outputFormat.flush();

        Assertions.assertEquals(2, executor.prepareStatementsCalls);
        Assertions.assertEquals(2, executor.executeBatchCalls);
        Assertions.assertEquals(1, executor.closeStatementsCalls);
        Mockito.verify(provider, Mockito.never()).reestablishConnection();
    }

    @Test
    void testFlushRetryShouldReconnectWhenBatchNextExceptionHasConnectionSqlState()
            throws Exception {
        JdbcConnectionProvider provider = Mockito.mock(JdbcConnectionProvider.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(provider.getOrEstablishConnection()).thenReturn(connection);
        Mockito.when(provider.getConnection()).thenReturn(connection);
        Mockito.when(provider.isConnectionValid()).thenReturn(true, true);
        Mockito.when(provider.reestablishConnection()).thenReturn(connection);

        TestBatchNextConnectionErrorExecutor executor = new TestBatchNextConnectionErrorExecutor();

        NoSleepJdbcOutputFormat<SeaTunnelRow, TestBatchNextConnectionErrorExecutor> outputFormat =
                new NoSleepJdbcOutputFormat<>(provider, buildConnectionConfig(), () -> executor);
        outputFormat.open();
        outputFormat.writeRecord(new SeaTunnelRow(new Object[] {"AA"}));

        outputFormat.flush();

        Assertions.assertEquals(2, executor.prepareStatementsCalls);
        Assertions.assertEquals(2, executor.executeBatchCalls);
        Assertions.assertEquals(1, executor.closeStatementsCalls);
        Mockito.verify(provider).reestablishConnection();
    }

    @Test
    void testFlushRetryShouldReprepareStatementsWhenBufferedExecutorStillHasRows()
            throws Exception {
        JdbcConnectionProvider provider = Mockito.mock(JdbcConnectionProvider.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(provider.getOrEstablishConnection()).thenReturn(connection);
        Mockito.when(provider.getConnection()).thenReturn(connection);
        Mockito.when(provider.isConnectionValid()).thenReturn(true, true);

        TrackingStatementClosedExecutor innerExecutor = new TrackingStatementClosedExecutor();
        JdbcBatchStatementExecutor<SeaTunnelRow> executor =
                new BufferedBatchStatementExecutor(innerExecutor, Function.identity());

        NoSleepJdbcOutputFormat<SeaTunnelRow, JdbcBatchStatementExecutor<SeaTunnelRow>>
                outputFormat =
                        new NoSleepJdbcOutputFormat<>(
                                provider, buildConnectionConfig(), () -> executor);
        outputFormat.open();
        outputFormat.writeRecord(insertRow("AA"));

        outputFormat.flush();

        Assertions.assertEquals(2, innerExecutor.prepareStatementsCalls);
        Assertions.assertEquals(2, innerExecutor.executeBatchCalls);
        Assertions.assertEquals(0, innerExecutor.closeStatementsCalls);
        Assertions.assertEquals(1, innerExecutor.closeStatementsForRecoveryCalls);
        Mockito.verify(provider, Mockito.never()).reestablishConnection();
    }

    @Test
    void testFlushRetryShouldReprepareStatementsWhenBufferReducedExecutorStillHasRows()
            throws Exception {
        JdbcConnectionProvider provider = Mockito.mock(JdbcConnectionProvider.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(provider.getOrEstablishConnection()).thenReturn(connection);
        Mockito.when(provider.getConnection()).thenReturn(connection);
        Mockito.when(provider.isConnectionValid()).thenReturn(true, true);

        TrackingStatementClosedExecutor upsertExecutor = new TrackingStatementClosedExecutor();
        TrackingJdbcBatchExecutor deleteExecutor = new TrackingJdbcBatchExecutor();
        JdbcBatchStatementExecutor<SeaTunnelRow> executor =
                new BufferReducedBatchStatementExecutor(
                        upsertExecutor, deleteExecutor, Function.identity(), Function.identity());

        NoSleepJdbcOutputFormat<SeaTunnelRow, JdbcBatchStatementExecutor<SeaTunnelRow>>
                outputFormat =
                        new NoSleepJdbcOutputFormat<>(
                                provider, buildConnectionConfig(), () -> executor);
        outputFormat.open();
        outputFormat.writeRecord(insertRow("AA"));

        outputFormat.flush();

        Assertions.assertEquals(2, upsertExecutor.prepareStatementsCalls);
        Assertions.assertEquals(2, upsertExecutor.executeBatchCalls);
        Assertions.assertEquals(0, upsertExecutor.closeStatementsCalls);
        Assertions.assertEquals(1, upsertExecutor.closeStatementsForRecoveryCalls);
        Assertions.assertEquals(2, deleteExecutor.prepareStatementsCalls);
        Assertions.assertEquals(0, deleteExecutor.closeStatementsCalls);
        Assertions.assertEquals(1, deleteExecutor.closeStatementsForRecoveryCalls);
        Mockito.verify(provider, Mockito.never()).reestablishConnection();
    }

    @Test
    void testFlushRetryShouldReconnectWithoutFlushingBufferedExecutorDuringRecovery()
            throws Exception {
        JdbcConnectionProvider provider = Mockito.mock(JdbcConnectionProvider.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(provider.getOrEstablishConnection()).thenReturn(connection);
        Mockito.when(provider.getConnection()).thenReturn(connection);
        Mockito.when(provider.isConnectionValid()).thenReturn(true, false);
        Mockito.when(provider.reestablishConnection()).thenReturn(connection);

        TrackingStatementClosedExecutor innerExecutor = new TrackingStatementClosedExecutor();
        JdbcBatchStatementExecutor<SeaTunnelRow> executor =
                new BufferedBatchStatementExecutor(innerExecutor, Function.identity());

        NoSleepJdbcOutputFormat<SeaTunnelRow, JdbcBatchStatementExecutor<SeaTunnelRow>>
                outputFormat =
                        new NoSleepJdbcOutputFormat<>(
                                provider, buildConnectionConfig(), () -> executor);
        outputFormat.open();
        outputFormat.writeRecord(insertRow("AA"));

        outputFormat.flush();

        Assertions.assertEquals(2, innerExecutor.prepareStatementsCalls);
        Assertions.assertEquals(2, innerExecutor.executeBatchCalls);
        Assertions.assertEquals(0, innerExecutor.closeStatementsCalls);
        Assertions.assertEquals(1, innerExecutor.closeStatementsForRecoveryCalls);
        Mockito.verify(provider).reestablishConnection();
    }

    @Test
    void testFlushRetryShouldReconnectWithoutFlushingBufferReducedExecutorDuringRecovery()
            throws Exception {
        JdbcConnectionProvider provider = Mockito.mock(JdbcConnectionProvider.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(provider.getOrEstablishConnection()).thenReturn(connection);
        Mockito.when(provider.getConnection()).thenReturn(connection);
        Mockito.when(provider.isConnectionValid()).thenReturn(true, false);
        Mockito.when(provider.reestablishConnection()).thenReturn(connection);

        TrackingStatementClosedExecutor upsertExecutor = new TrackingStatementClosedExecutor();
        TrackingJdbcBatchExecutor deleteExecutor = new TrackingJdbcBatchExecutor();
        JdbcBatchStatementExecutor<SeaTunnelRow> executor =
                new BufferReducedBatchStatementExecutor(
                        upsertExecutor, deleteExecutor, Function.identity(), Function.identity());

        NoSleepJdbcOutputFormat<SeaTunnelRow, JdbcBatchStatementExecutor<SeaTunnelRow>>
                outputFormat =
                        new NoSleepJdbcOutputFormat<>(
                                provider, buildConnectionConfig(), () -> executor);
        outputFormat.open();
        outputFormat.writeRecord(insertRow("AA"));

        outputFormat.flush();

        Assertions.assertEquals(2, upsertExecutor.prepareStatementsCalls);
        Assertions.assertEquals(2, upsertExecutor.executeBatchCalls);
        Assertions.assertEquals(0, upsertExecutor.closeStatementsCalls);
        Assertions.assertEquals(1, upsertExecutor.closeStatementsForRecoveryCalls);
        Assertions.assertEquals(2, deleteExecutor.prepareStatementsCalls);
        Assertions.assertEquals(0, deleteExecutor.closeStatementsCalls);
        Assertions.assertEquals(1, deleteExecutor.closeStatementsForRecoveryCalls);
        Mockito.verify(provider).reestablishConnection();
    }

    private JdbcConnectionConfig buildConnectionConfig() {
        return JdbcConnectionConfig.builder()
                .url("jdbc:postgresql://localhost:5432/test")
                .maxRetries(2)
                .batchSize(1024)
                .build();
    }

    private SeaTunnelRow insertRow(String value) {
        SeaTunnelRow row = new SeaTunnelRow(new Object[] {value});
        row.setRowKind(RowKind.INSERT);
        return row;
    }

    private static BatchUpdateException batchException(
            String message, String sqlState, SQLException nextException) {
        BatchUpdateException exception = new BatchUpdateException(message, sqlState, new int[0]);
        exception.setNextException(nextException);
        return exception;
    }

    private static class TestCopyExecutor implements CopyBatchStatementExecutor {
        private boolean flushed = true;
        private SQLException flushFailure;
        private int executeBatchCalls;
        private int closeStatementsCalls;

        @Override
        public String dialectName() {
            return "postgresql";
        }

        @Override
        public void init(TablePath tablePath, TableSchema tableSchema) {}

        @Override
        public boolean isFlushed() {
            return flushed;
        }

        @Override
        public void prepareStatements(Connection connection) {}

        @Override
        public void addToBatch(SeaTunnelRow record) {
            flushed = false;
        }

        @Override
        public void executeBatch() throws SQLException {
            executeBatchCalls++;
            if (flushFailure != null) {
                throw flushFailure;
            }
            flushed = true;
        }

        @Override
        public void closeStatements() {
            closeStatementsCalls++;
        }
    }

    private static class TestJdbcBatchExecutor implements JdbcBatchStatementExecutor<SeaTunnelRow> {
        private boolean failedOnce;
        private boolean statementClosed;
        private int prepareStatementsCalls;
        private int executeBatchCalls;
        private int closeStatementsCalls;
        private final String statementClosedMessage;

        private TestJdbcBatchExecutor() {
            this("No operations allowed after statement closed.");
        }

        private TestJdbcBatchExecutor(String statementClosedMessage) {
            this.statementClosedMessage = statementClosedMessage;
        }

        @Override
        public void prepareStatements(Connection connection) {
            prepareStatementsCalls++;
            statementClosed = false;
        }

        @Override
        public void addToBatch(SeaTunnelRow record) {}

        @Override
        public void executeBatch() throws SQLException {
            executeBatchCalls++;
            if (!failedOnce) {
                failedOnce = true;
                statementClosed = true;
                throw new SQLException(statementClosedMessage);
            }
            if (statementClosed) {
                throw new SQLException(statementClosedMessage);
            }
        }

        @Override
        public void closeStatements() {
            closeStatementsCalls++;
        }
    }

    private static class TestConnectionErrorExecutor
            implements JdbcBatchStatementExecutor<SeaTunnelRow> {
        private boolean failedOnce;
        private int prepareStatementsCalls;
        private int executeBatchCalls;
        private int closeStatementsCalls;

        @Override
        public void prepareStatements(Connection connection) {
            prepareStatementsCalls++;
        }

        @Override
        public void addToBatch(SeaTunnelRow record) {}

        @Override
        public void executeBatch() throws SQLException {
            executeBatchCalls++;
            if (!failedOnce) {
                failedOnce = true;
                throw new SQLException(
                        "The last packet sent successfully is longer than wait_timeout.",
                        "08S01",
                        new IOException("Socket is closed"));
            }
        }

        @Override
        public void closeStatements() {
            closeStatementsCalls++;
        }
    }

    private static class TestWrappedConnectionErrorExecutor
            implements JdbcBatchStatementExecutor<SeaTunnelRow> {
        private boolean failedOnce;
        private int prepareStatementsCalls;
        private int executeBatchCalls;
        private int closeStatementsCalls;

        @Override
        public void prepareStatements(Connection connection) {
            prepareStatementsCalls++;
        }

        @Override
        public void addToBatch(SeaTunnelRow record) {}

        @Override
        public void executeBatch() throws SQLException {
            executeBatchCalls++;
            if (!failedOnce) {
                failedOnce = true;
                throw new SQLException(
                        "outer wrapper",
                        "HY000",
                        new SQLException("inner connection dropped", "08006"));
            }
        }

        @Override
        public void closeStatements() {
            closeStatementsCalls++;
        }
    }

    private static class TestBatchNextStatementClosedExecutor
            implements JdbcBatchStatementExecutor<SeaTunnelRow> {
        private boolean failedOnce;
        private boolean statementClosed;
        private int prepareStatementsCalls;
        private int executeBatchCalls;
        private int closeStatementsCalls;

        @Override
        public void prepareStatements(Connection connection) {
            prepareStatementsCalls++;
            statementClosed = false;
        }

        @Override
        public void addToBatch(SeaTunnelRow record) {}

        @Override
        public void executeBatch() throws SQLException {
            executeBatchCalls++;
            if (!failedOnce) {
                failedOnce = true;
                statementClosed = true;
                throw batchException(
                        "batch failed",
                        "HY000",
                        new SQLException("No operations allowed after statement closed."));
            }
            if (statementClosed) {
                throw new SQLException("No operations allowed after statement closed.");
            }
        }

        @Override
        public void closeStatements() {
            closeStatementsCalls++;
        }
    }

    private static class TestBatchNextConnectionErrorExecutor
            implements JdbcBatchStatementExecutor<SeaTunnelRow> {
        private boolean failedOnce;
        private int prepareStatementsCalls;
        private int executeBatchCalls;
        private int closeStatementsCalls;

        @Override
        public void prepareStatements(Connection connection) {
            prepareStatementsCalls++;
        }

        @Override
        public void addToBatch(SeaTunnelRow record) {}

        @Override
        public void executeBatch() throws SQLException {
            executeBatchCalls++;
            if (!failedOnce) {
                failedOnce = true;
                throw batchException(
                        "batch failed", "HY000", new SQLException("connection dropped", "08006"));
            }
        }

        @Override
        public void closeStatements() {
            closeStatementsCalls++;
        }
    }

    private static class TrackingJdbcBatchExecutor
            implements JdbcBatchStatementExecutor<SeaTunnelRow> {
        protected int prepareStatementsCalls;
        protected int closeStatementsCalls;
        protected int closeStatementsForRecoveryCalls;

        @Override
        public void prepareStatements(Connection connection) {
            prepareStatementsCalls++;
        }

        @Override
        public void addToBatch(SeaTunnelRow record) {}

        @Override
        public void executeBatch() throws SQLException {}

        @Override
        public void closeStatements() {
            closeStatementsCalls++;
        }

        @Override
        public void closeStatementsForRecovery() {
            closeStatementsForRecoveryCalls++;
        }
    }

    private static class TrackingStatementClosedExecutor extends TrackingJdbcBatchExecutor {
        private boolean failedOnce;
        private boolean statementClosed;
        private int executeBatchCalls;

        @Override
        public void prepareStatements(Connection connection) {
            super.prepareStatements(connection);
            statementClosed = false;
        }

        @Override
        public void executeBatch() throws SQLException {
            executeBatchCalls++;
            if (!failedOnce) {
                failedOnce = true;
                statementClosed = true;
                throw new SQLException("No operations allowed after statement closed.");
            }
            if (statementClosed) {
                throw new SQLException("No operations allowed after statement closed.");
            }
        }
    }

    private static class NoSleepJdbcOutputFormat<I, E extends JdbcBatchStatementExecutor<I>>
            extends JdbcOutputFormat<I, E> {

        private NoSleepJdbcOutputFormat(
                JdbcConnectionProvider connectionProvider,
                JdbcConnectionConfig jdbcConnectionConfig,
                StatementExecutorFactory<E> statementExecutorFactory) {
            super(connectionProvider, jdbcConnectionConfig, statementExecutorFactory);
        }

        @Override
        protected void sleepBeforeFlushRetry(long backoffMs) {}
    }
}

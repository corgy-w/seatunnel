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
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.connectors.seatunnel.jdbc.config.JdbcConnectionConfig;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.connection.JdbcConnectionProvider;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.executor.CopyBatchStatementExecutor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.SQLException;

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
        Assertions.assertTrue(exception.getMessage().contains("Unsafe reconnect is blocked"));
        Assertions.assertEquals(1, executor.executeBatchCalls);
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
                exception.getCause().getMessage().contains("Unsafe reconnect is blocked"));
        Assertions.assertEquals(0, executor.executeBatchCalls);
        Mockito.verify(provider, Mockito.never()).reestablishConnection();
    }

    private JdbcConnectionConfig buildConnectionConfig() {
        return JdbcConnectionConfig.builder()
                .url("jdbc:postgresql://localhost:5432/test")
                .maxRetries(2)
                .batchSize(1024)
                .build();
    }

    private static class TestCopyExecutor implements CopyBatchStatementExecutor {
        private boolean flushed = true;
        private SQLException flushFailure;
        private int executeBatchCalls;

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
        public void closeStatements() {}
    }
}

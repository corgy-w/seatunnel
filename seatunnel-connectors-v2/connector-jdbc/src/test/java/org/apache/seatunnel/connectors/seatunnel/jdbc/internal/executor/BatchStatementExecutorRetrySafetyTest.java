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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.executor;

import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.converter.JdbcRowConverter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

class BatchStatementExecutorRetrySafetyTest {

    @Test
    void testSimpleExecutorShouldClearBatchWhenExecuteFails() throws Exception {
        StatementFactory statementFactory = Mockito.mock(StatementFactory.class);
        JdbcRowConverter converter = Mockito.mock(JdbcRowConverter.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        Mockito.when(statementFactory.createStatement(connection)).thenReturn(statement);
        Mockito.doThrow(new SQLException("execute failed")).when(statement).executeBatch();

        SimpleBatchStatementExecutor executor =
                new SimpleBatchStatementExecutor(
                        statementFactory, TableSchema.builder().build(), null, converter);
        executor.prepareStatements(connection);

        Assertions.assertThrows(SQLException.class, executor::executeBatch);
        Mockito.verify(statement).clearBatch();
    }

    @Test
    void testInsertOrUpdateExecutorShouldClearBatchWhenExecuteFails() throws Exception {
        StatementFactory insertFactory = Mockito.mock(StatementFactory.class);
        StatementFactory updateFactory = Mockito.mock(StatementFactory.class);
        JdbcRowConverter converter = Mockito.mock(JdbcRowConverter.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement insertStatement = Mockito.mock(PreparedStatement.class);
        PreparedStatement updateStatement = Mockito.mock(PreparedStatement.class);
        Mockito.when(insertFactory.createStatement(connection)).thenReturn(insertStatement);
        Mockito.when(updateFactory.createStatement(connection)).thenReturn(updateStatement);
        Mockito.doThrow(new SQLException("execute failed")).when(updateStatement).executeBatch();

        InsertOrUpdateBatchStatementExecutor executor =
                new InsertOrUpdateBatchStatementExecutor(
                        insertFactory,
                        updateFactory,
                        TableSchema.builder().build(),
                        null,
                        converter);
        executor.prepareStatements(connection);

        SeaTunnelRow updateRow = new SeaTunnelRow(new Object[] {1});
        updateRow.setRowKind(RowKind.UPDATE_AFTER);
        executor.addToBatch(updateRow);

        Assertions.assertThrows(SQLException.class, executor::executeBatch);
        Mockito.verify(updateStatement).clearBatch();
    }

    @Test
    void testInsertOrUpdateSwitchShouldClearPreviousBatchWhenExecuteFails() throws Exception {
        StatementFactory insertFactory = Mockito.mock(StatementFactory.class);
        StatementFactory updateFactory = Mockito.mock(StatementFactory.class);
        JdbcRowConverter converter = Mockito.mock(JdbcRowConverter.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement insertStatement = Mockito.mock(PreparedStatement.class);
        PreparedStatement updateStatement = Mockito.mock(PreparedStatement.class);
        Mockito.when(insertFactory.createStatement(connection)).thenReturn(insertStatement);
        Mockito.when(updateFactory.createStatement(connection)).thenReturn(updateStatement);
        Mockito.doThrow(new SQLException("execute failed")).when(insertStatement).executeBatch();

        InsertOrUpdateBatchStatementExecutor executor =
                new InsertOrUpdateBatchStatementExecutor(
                        insertFactory,
                        updateFactory,
                        TableSchema.builder().build(),
                        null,
                        converter);
        executor.prepareStatements(connection);

        SeaTunnelRow insertRow = new SeaTunnelRow(new Object[] {1});
        insertRow.setRowKind(RowKind.INSERT);
        executor.addToBatch(insertRow);

        SeaTunnelRow updateRow = new SeaTunnelRow(new Object[] {1});
        updateRow.setRowKind(RowKind.UPDATE_AFTER);

        Assertions.assertThrows(SQLException.class, () -> executor.addToBatch(updateRow));
        Mockito.verify(insertStatement).clearBatch();
    }
}

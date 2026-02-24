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
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.converter.JdbcRowConverter;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@RequiredArgsConstructor
public class SimpleBatchStatementExecutor implements JdbcBatchStatementExecutor<SeaTunnelRow> {
    @NonNull private final StatementFactory statementFactory;
    @NonNull private final TableSchema tableSchema;
    @Nullable private final TableSchema databaseTableSchema;
    @NonNull private final JdbcRowConverter converter;
    private transient PreparedStatement statement;

    @Override
    public void prepareStatements(Connection connection) throws SQLException {
        statement = statementFactory.createStatement(connection);
    }

    @Override
    public void addToBatch(SeaTunnelRow record) throws SQLException {
        converter.toExternal(tableSchema, databaseTableSchema, record, statement);
        statement.addBatch();
    }

    @Override
    public void executeBatch() throws SQLException {
        executeAndClearBatch(statement);
    }

    @Override
    public void closeStatements() throws SQLException {
        if (statement != null) {
            statement.close();
        }
    }

    /**
     * Always clears JDBC driver-side batched parameters after an execution attempt.
     *
     * <p>Without this, a failed executeBatch() can leave dirty rows in PreparedStatement, and upper
     * retry logic may add the same rows again, causing duplicate writes.
     */
    private static void executeAndClearBatch(PreparedStatement statement) throws SQLException {
        SQLException executeException = null;
        try {
            statement.executeBatch();
        } catch (SQLException e) {
            executeException = e;
            throw e;
        } finally {
            try {
                statement.clearBatch();
            } catch (SQLException clearException) {
                if (executeException != null) {
                    executeException.addSuppressed(clearException);
                } else {
                    throw clearException;
                }
            }
        }
    }
}

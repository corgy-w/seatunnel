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
import org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.converter.JdbcRowConverter;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.function.Function;

@RequiredArgsConstructor
public class InsertOrUpdateBatchStatementExecutor
        implements JdbcBatchStatementExecutor<SeaTunnelRow> {
    private final StatementFactory existStmtFactory;
    @NonNull private final StatementFactory insertStmtFactory;
    @NonNull private final StatementFactory updateStmtFactory;
    private final TableSchema keyTableSchema;
    private final Function<SeaTunnelRow, SeaTunnelRow> keyExtractor;
    @NonNull private final TableSchema valueTableSchema;
    @Nullable private final TableSchema databaseTableSchema;
    @NonNull private final JdbcRowConverter rowConverter;
    private transient PreparedStatement existStatement;
    private transient PreparedStatement insertStatement;
    private transient PreparedStatement updateStatement;
    private transient Boolean preExistFlag;
    private transient boolean submitted;

    public InsertOrUpdateBatchStatementExecutor(
            StatementFactory insertStmtFactory,
            StatementFactory updateStmtFactory,
            TableSchema valueTableSchema,
            TableSchema databaseTableSchema,
            JdbcRowConverter rowConverter) {
        this(
                null,
                insertStmtFactory,
                updateStmtFactory,
                null,
                null,
                valueTableSchema,
                databaseTableSchema,
                rowConverter);
    }

    @Override
    public void prepareStatements(Connection connection) throws SQLException {
        if (upsertMode()) {
            existStatement = existStmtFactory.createStatement(connection);
        }
        insertStatement = insertStmtFactory.createStatement(connection);
        updateStatement = updateStmtFactory.createStatement(connection);
    }

    @Override
    public void addToBatch(SeaTunnelRow record) throws SQLException {
        boolean exist = existRow(record);
        if (exist) {
            if (preExistFlag != null && !preExistFlag) {
                executeAndClearBatch(insertStatement);
            }
            rowConverter.toExternal(valueTableSchema, databaseTableSchema, record, updateStatement);
            updateStatement.addBatch();
        } else {
            if (preExistFlag != null && preExistFlag) {
                executeAndClearBatch(updateStatement);
            }
            rowConverter.toExternal(valueTableSchema, databaseTableSchema, record, insertStatement);
            insertStatement.addBatch();
        }

        preExistFlag = exist;
        submitted = false;
    }

    @Override
    public void executeBatch() throws SQLException {
        if (preExistFlag != null) {
            if (preExistFlag) {
                executeAndClearBatch(updateStatement);
            } else {
                executeAndClearBatch(insertStatement);
            }
        }
        submitted = true;
    }

    @Override
    public void closeStatements() throws SQLException {
        try {
            if (!submitted) {
                executeBatch();
            }
        } finally {
            for (PreparedStatement statement :
                    Arrays.asList(existStatement, insertStatement, updateStatement)) {
                if (statement != null) {
                    statement.close();
                }
            }
        }
    }

    @Override
    public void closeStatementsForRecovery() throws SQLException {
        for (PreparedStatement statement :
                Arrays.asList(existStatement, insertStatement, updateStatement)) {
            if (statement != null) {
                statement.close();
            }
        }
    }

    private boolean upsertMode() {
        return existStmtFactory != null;
    }

    private boolean existRow(SeaTunnelRow record) throws SQLException {
        if (upsertMode()) {
            return exist(keyExtractor.apply(record));
        }
        switch (record.getRowKind()) {
            case INSERT:
            case READ:
                return false;
            case UPDATE_AFTER:
                return true;
            default:
                throw new JdbcConnectorException(
                        CommonErrorCodeDeprecated.UNSUPPORTED_OPERATION,
                        "unsupported row kind: " + record.getRowKind());
        }
    }

    private boolean exist(SeaTunnelRow pk) throws SQLException {
        rowConverter.toExternal(keyTableSchema, databaseTableSchema, pk, existStatement);
        try (ResultSet resultSet = existStatement.executeQuery()) {
            return resultSet.next();
        }
    }

    /**
     * Always clears JDBC driver-side batch entries even if executeBatch fails, preventing stale
     * parameters from being re-executed by upper retry logic.
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

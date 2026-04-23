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

import org.apache.seatunnel.common.exception.CommonErrorCodeDeprecated;
import org.apache.seatunnel.common.utils.ExceptionUtils;
import org.apache.seatunnel.connectors.seatunnel.jdbc.config.JdbcConnectionConfig;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.connection.JdbcConnectionProvider;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.executor.CopyBatchStatementExecutor;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.executor.DynamicBufferedBatchStatementExecutor;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.executor.JdbcBatchStatementExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

/** A JDBC outputFormat */
public class JdbcOutputFormat<I, E extends JdbcBatchStatementExecutor<I>> implements Serializable {

    protected final JdbcConnectionProvider connectionProvider;

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(JdbcOutputFormat.class);

    private final JdbcConnectionConfig jdbcConnectionConfig;
    private final StatementExecutorFactory<E> statementExecutorFactory;

    private transient E jdbcStatementExecutor;
    private transient int batchCount = 0;
    private transient volatile boolean closed = false;
    private transient volatile Exception flushException;

    public JdbcOutputFormat(
            JdbcConnectionProvider connectionProvider,
            JdbcConnectionConfig jdbcConnectionConfig,
            StatementExecutorFactory<E> statementExecutorFactory) {
        this.connectionProvider = checkNotNull(connectionProvider);
        this.jdbcConnectionConfig = checkNotNull(jdbcConnectionConfig);
        this.statementExecutorFactory = checkNotNull(statementExecutorFactory);
    }

    /** Connects to the target database and initializes the prepared statement. */
    public void open() throws IOException {
        try {
            connectionProvider.getOrEstablishConnection();
        } catch (Exception e) {
            throw new JdbcConnectorException(
                    JdbcConnectorErrorCode.CONNECT_DATABASE_FAILED,
                    "unable to open JDBC writer",
                    e);
        }
        jdbcStatementExecutor = createAndOpenStatementExecutor(statementExecutorFactory);
    }

    private E createAndOpenStatementExecutor(StatementExecutorFactory<E> statementExecutorFactory) {
        E exec = statementExecutorFactory.get();
        try {
            exec.prepareStatements(connectionProvider.getConnection());
        } catch (SQLException e) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.SQL_OPERATION_FAILED,
                    "unable to open JDBC writer",
                    e);
        }
        return exec;
    }

    public void checkFlushException() {
        if (flushException != null) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.FLUSH_DATA_FAILED,
                    "Writing records to JDBC failed.",
                    flushException);
        }
    }

    public final synchronized void writeRecord(I record) {
        checkFlushException();
        try {
            addToBatch(record);
            batchCount++;
            if (jdbcConnectionConfig.getBatchSize() > 0
                    && batchCount >= jdbcConnectionConfig.getBatchSize()) {
                flush();
            }
        } catch (Exception e) {
            throw new JdbcConnectorException(
                    CommonErrorCodeDeprecated.SQL_OPERATION_FAILED,
                    "Writing records to JDBC failed.",
                    e);
        }
    }

    protected void addToBatch(I record) throws SQLException {
        jdbcStatementExecutor.addToBatch(record);
    }

    private void testOnBorrow() {
        try {
            if (jdbcConnectionConfig.getUrl().startsWith("jdbc:phoenix:thin")) {
                return;
            }
            if (!connectionProvider.isConnectionValid()) {
                LOG.debug("Connection is invalid, try to reconnect.");
                throwIfUnsafeToRecoverExecutor(
                        0, jdbcConnectionConfig.getMaxRetries(), null, false);
                updateExecutor(true);
            }
        } catch (Exception e) {
            throw new JdbcConnectorException(
                    JdbcConnectorErrorCode.CONNECT_DATABASE_FAILED,
                    "Reestablish JDBC connection failed",
                    e);
        }
    }

    public synchronized void flush() throws IOException {
        if (flushException != null) {
            LOG.warn(
                    String.format(
                            "An exception occurred during the previous flush process %s, skipping this flush",
                            ExceptionUtils.getMessage(flushException)));
            return;
        }

        if (batchCount == 0) {
            LOG.debug("Skip flush: no buffered records.");
            return;
        }

        // keep original behavior
        testOnBorrow();

        final int maxRetries = jdbcConnectionConfig.getMaxRetries();
        final long baseBackoffMs = 1000L;
        final long maxBackoffMs = 10_000L;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                LOG.debug(
                        "Start flush. batchCount={}, attempt={}/{}",
                        batchCount,
                        attempt,
                        maxRetries);
                attemptFlush();
                batchCount = 0;
                LOG.debug("Flush success. attempt={}/{}", attempt, maxRetries);
                return;
            } catch (Exception e) {
                final List<SQLException> sqlExceptions = findSqlExceptions(e);
                final SQLException sqlEx = sqlExceptions.isEmpty() ? null : sqlExceptions.get(0);

                // keep original behavior: no SQL exception in cause chain -> no retry
                if (sqlEx == null) {
                    LOG.error(
                            "Flush failed (non-SQL). batchCount={}, attempt={}/{}",
                            batchCount,
                            attempt,
                            maxRetries,
                            e);
                    throw new JdbcConnectorException(
                            CommonErrorCodeDeprecated.FLUSH_DATA_FAILED, e);
                }

                final String connectionErrorSqlState = findConnectionErrorSqlState(sqlExceptions);
                final String sqlState =
                        connectionErrorSqlState != null
                                ? connectionErrorSqlState
                                : sqlEx.getSQLState();
                final boolean sqlStateConnError = connectionErrorSqlState != null;
                final boolean statementClosed = isStatementClosed(sqlExceptions);

                boolean connValid = true;
                try {
                    connValid = connectionProvider.isConnectionValid();
                } catch (Exception validCheckEx) {
                    // validity check itself failed -> treat as invalid
                    connValid = false;
                    LOG.warn(
                            "Connection validity check failed, treat as invalid. attempt={}/{}",
                            attempt,
                            maxRetries,
                            validCheckEx);
                }

                final boolean needReconnect = sqlStateConnError || !connValid;
                final boolean needReprepareStatements =
                        statementClosed && connValid && !sqlStateConnError;

                if (attempt >= maxRetries) {
                    LOG.error(
                            "Flush failed after retries. batchCount={}, attempts={}, sqlState={}, connValid={}, statementClosed={}, needReconnect={}, needReprepareStatements={}",
                            batchCount,
                            attempt,
                            sqlState,
                            connValid,
                            statementClosed,
                            needReconnect,
                            needReprepareStatements,
                            e);
                    throw new JdbcConnectorException(
                            CommonErrorCodeDeprecated.FLUSH_DATA_FAILED, e);
                }

                long backoffMs = Math.min(baseBackoffMs * (attempt + 1L), maxBackoffMs);

                LOG.debug(
                        "Flush failed, will retry. batchCount={}, attempt={}/{}, sqlState={}, connValid={}, statementClosed={}, needReconnect={}, needReprepareStatements={}, backoffMs={}",
                        batchCount,
                        attempt,
                        maxRetries,
                        sqlState,
                        connValid,
                        statementClosed,
                        needReconnect,
                        needReprepareStatements,
                        backoffMs,
                        e);

                if (needReconnect) {
                    throwIfUnsafeToRecoverExecutor(attempt, maxRetries, sqlState, connValid);
                    recoverStatementExecutor(
                            true, attempt, maxRetries, sqlState, connValid, statementClosed);
                } else if (needReprepareStatements) {
                    throwIfUnsafeToRecoverExecutor(attempt, maxRetries, sqlState, connValid);
                    recoverStatementExecutor(
                            false, attempt, maxRetries, sqlState, connValid, statementClosed);
                }

                try {
                    sleepBeforeFlushRetry(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new JdbcConnectorException(
                            CommonErrorCodeDeprecated.FLUSH_DATA_FAILED,
                            "unable to flush; interrupted while backing off for retry",
                            ie);
                }
            }
        }
    }

    /**
     * Block executor recovery when COPY payload is still buffered locally. Reconnecting or
     * re-preparing in this state may lose in-memory data that has not been persisted yet.
     */
    private void throwIfUnsafeToRecoverExecutor(
            int attempt, int maxRetries, String sqlState, boolean connValid) {
        if (!hasPendingCopyBuffer()) {
            return;
        }
        throw new JdbcConnectorException(
                CommonErrorCodeDeprecated.FLUSH_DATA_FAILED,
                String.format(
                        "Unsafe JDBC executor recovery is blocked because COPY payload is still buffered. "
                                + "attempt=%d/%d, sqlState=%s, connValid=%s. "
                                + "Fail fast to avoid silent data loss.",
                        attempt, maxRetries, sqlState, connValid));
    }

    /**
     * Check if there is pending COPY buffer that has not been flushed to database yet.
     *
     * @return
     */
    private boolean hasPendingCopyBuffer() {
        if (jdbcStatementExecutor instanceof DynamicBufferedBatchStatementExecutor) {
            return ((DynamicBufferedBatchStatementExecutor) jdbcStatementExecutor)
                    .hasPendingCopyBuffer();
        }
        if (jdbcStatementExecutor instanceof CopyBatchStatementExecutor) {
            return !((CopyBatchStatementExecutor) jdbcStatementExecutor).isFlushed();
        }
        return false;
    }

    protected void attemptFlush() throws SQLException {
        jdbcStatementExecutor.executeBatch();
    }

    /** Executes prepared statement and closes all resources of this instance. */
    public synchronized void close() {
        if (!closed) {
            closed = true;

            if (batchCount > 0) {
                try {
                    flush();
                } catch (Exception e) {
                    LOG.warn("Writing records to JDBC failed.", e);
                    flushException =
                            new JdbcConnectorException(
                                    CommonErrorCodeDeprecated.FLUSH_DATA_FAILED,
                                    "Writing records to JDBC failed.",
                                    e);
                }
            }
            try {
                if (jdbcStatementExecutor != null) {
                    jdbcStatementExecutor.closeStatements();
                }
            } catch (Exception e) {
                LOG.warn("Close JDBC writer failed.", e);
                throw new JdbcConnectorException(
                        CommonErrorCodeDeprecated.FLUSH_DATA_FAILED,
                        "Close JDBC writer failed.",
                        e);
            }
            checkFlushException();
        }
    }

    public void updateExecutor(boolean reconnect) throws SQLException, ClassNotFoundException {
        SQLException closeEx = null;

        try {
            jdbcStatementExecutor.closeStatementsForRecovery();
        } catch (SQLException e) {
            closeEx = e;
            if (!reconnect) {
                throw e;
            }
            LOG.warn(
                    "Close JDBC statements failed during reconnect, will continue. cause={}",
                    e.getMessage(),
                    e);
        }

        try {
            jdbcStatementExecutor.prepareStatements(
                    reconnect
                            ? connectionProvider.reestablishConnection()
                            : connectionProvider.getConnection());
        } catch (SQLException | ClassNotFoundException e) {
            if (closeEx != null) {
                e.addSuppressed(closeEx);
            }
            throw e;
        }
    }

    protected void sleepBeforeFlushRetry(long backoffMs) throws InterruptedException {
        Thread.sleep(backoffMs);
    }

    private String findConnectionErrorSqlState(List<SQLException> sqlExceptions) {
        for (SQLException sqlException : sqlExceptions) {
            String sqlState = sqlException.getSQLState();
            if (sqlState != null && sqlState.startsWith("08")) {
                return sqlState;
            }
        }
        return null;
    }

    private List<SQLException> findSqlExceptions(Throwable throwable) {
        List<SQLException> sqlExceptions = new ArrayList<>();
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException) {
                collectSqlExceptionChain((SQLException) current, sqlExceptions);
            }
            current = current.getCause();
        }
        return sqlExceptions;
    }

    private void collectSqlExceptionChain(
            SQLException sqlException, List<SQLException> sqlExceptions) {
        SQLException current = sqlException;
        while (current != null) {
            sqlExceptions.add(current);
            current = current.getNextException();
        }
    }

    private boolean isStatementClosed(List<SQLException> sqlExceptions) {
        for (SQLException sqlException : sqlExceptions) {
            if (isStatementClosed(sqlException)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStatementClosed(SQLException sqlException) {
        String exceptionClassName =
                sqlException.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (exceptionClassName.contains("statementisclosedexception")) {
            return true;
        }

        String message = sqlException.getMessage();
        if (message == null) {
            return false;
        }

        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        // SQL Server may report a closed statement handle without using "closed".
        return normalizedMessage.contains("statement closed")
                || normalizedMessage.contains("statement is closed")
                || normalizedMessage.contains("statement handle is not executing")
                || normalizedMessage.contains("statement handle is closed");
    }

    private void recoverStatementExecutor(
            boolean reconnect,
            int attempt,
            int maxRetries,
            String sqlState,
            boolean connValid,
            boolean statementClosed) {
        try {
            LOG.info(
                    reconnect
                            ? "Reconnecting JDBC. attempt={}/{}, sqlState={}, connValid={}, statementClosed={}"
                            : "Re-preparing JDBC statements. attempt={}/{}, sqlState={}, connValid={}, statementClosed={}",
                    attempt,
                    maxRetries,
                    sqlState,
                    connValid,
                    statementClosed);
            updateExecutor(reconnect);
            LOG.info(
                    reconnect
                            ? "Reconnect JDBC success. attempt={}/{}"
                            : "Re-prepare JDBC statements success. attempt={}/{}",
                    attempt,
                    maxRetries);
        } catch (Exception recoveryEx) {
            LOG.error(
                    reconnect
                            ? "Reconnect JDBC failed. attempt={}/{}, sqlState={}, connValid={}, statementClosed={}"
                            : "Re-prepare JDBC statements failed. attempt={}/{}, sqlState={}, connValid={}, statementClosed={}",
                    attempt,
                    maxRetries,
                    sqlState,
                    connValid,
                    statementClosed,
                    recoveryEx);
            throw new JdbcConnectorException(
                    reconnect
                            ? JdbcConnectorErrorCode.CONNECT_DATABASE_FAILED
                            : CommonErrorCodeDeprecated.SQL_OPERATION_FAILED,
                    reconnect
                            ? "Reestablish JDBC connection failed"
                            : "Reprepare JDBC statements failed",
                    recoveryEx);
        }
    }

    /**
     * A factory for creating {@link JdbcBatchStatementExecutor} instance.
     *
     * @param <T> The type of instance.
     */
    public interface StatementExecutorFactory<T extends JdbcBatchStatementExecutor<?>>
            extends Supplier<T>, Serializable {}
}

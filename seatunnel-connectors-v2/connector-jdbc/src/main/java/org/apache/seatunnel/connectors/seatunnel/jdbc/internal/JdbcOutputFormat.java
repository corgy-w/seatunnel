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
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.executor.JdbcBatchStatementExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
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
                final Throwable root = Throwables.getRootCause(e);

                // keep original behavior: non-SQL root cause -> no retry
                if (!(root instanceof SQLException)) {
                    LOG.error(
                            "Flush failed (non-SQL). batchCount={}, attempt={}/{}",
                            batchCount,
                            attempt,
                            maxRetries,
                            e);
                    throw new JdbcConnectorException(
                            CommonErrorCodeDeprecated.FLUSH_DATA_FAILED, e);
                }

                final SQLException sqlEx = (SQLException) root;
                final String sqlState = sqlEx.getSQLState();
                final boolean sqlStateConnError = sqlState != null && sqlState.startsWith("08");

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

                if (attempt >= maxRetries) {
                    LOG.error(
                            "Flush failed after retries. batchCount={}, attempts={}, sqlState={}, connValid={}, needReconnect={}",
                            batchCount,
                            attempt,
                            sqlState,
                            connValid,
                            needReconnect,
                            e);
                    throw new JdbcConnectorException(
                            CommonErrorCodeDeprecated.FLUSH_DATA_FAILED, e);
                }

                long backoffMs = Math.min(baseBackoffMs * (attempt + 1L), maxBackoffMs);

                LOG.debug(
                        "Flush failed, will retry. batchCount={}, attempt={}/{}, sqlState={}, connValid={}, needReconnect={}, backoffMs={}",
                        batchCount,
                        attempt,
                        maxRetries,
                        sqlState,
                        connValid,
                        needReconnect,
                        backoffMs,
                        e);

                if (needReconnect) {
                    try {
                        LOG.info(
                                "Reconnecting JDBC. attempt={}/{}, sqlState={}, connValid={}",
                                attempt,
                                maxRetries,
                                sqlState,
                                connValid);
                        updateExecutor(true);
                        LOG.info("Reconnect JDBC success. attempt={}/{}", attempt, maxRetries);
                    } catch (Exception reconnectEx) {
                        LOG.error(
                                "Reconnect JDBC failed. attempt={}/{}, sqlState={}, connValid={}",
                                attempt,
                                maxRetries,
                                sqlState,
                                connValid,
                                reconnectEx);
                        throw new JdbcConnectorException(
                                JdbcConnectorErrorCode.CONNECT_DATABASE_FAILED,
                                "Reestablish JDBC connection failed",
                                reconnectEx);
                    }
                }

                try {
                    Thread.sleep(backoffMs);
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
            jdbcStatementExecutor.closeStatements();
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

    /**
     * A factory for creating {@link JdbcBatchStatementExecutor} instance.
     *
     * @param <T> The type of instance.
     */
    public interface StatementExecutorFactory<T extends JdbcBatchStatementExecutor<?>>
            extends Supplier<T>, Serializable {}
}

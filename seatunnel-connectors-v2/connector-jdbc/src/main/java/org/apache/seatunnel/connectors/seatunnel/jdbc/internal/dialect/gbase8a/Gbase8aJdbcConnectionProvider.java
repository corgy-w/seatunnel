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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.gbase8a;

import org.apache.seatunnel.connectors.seatunnel.jdbc.config.JdbcConnectionConfig;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.connection.SimpleJdbcConnectionProvider;

import lombok.NonNull;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/** GBase8a JDBC connection provider with VC-aware support */
public class Gbase8aJdbcConnectionProvider extends SimpleJdbcConnectionProvider {

    public Gbase8aJdbcConnectionProvider(@NonNull JdbcConnectionConfig jdbcConfig) {
        super(jdbcConfig);
    }

    @Override
    public Connection getOrEstablishConnection() throws SQLException, ClassNotFoundException {
        if (isConnectionValid()) {
            return super.getConnection();
        }
        Driver driver = getLoadedDriver();
        Properties info = buildConnectionProperties();
        Connection connection = createGbase8aConnection(driver, info);
        if (connection == null) {
            throw new JdbcConnectorException(
                    JdbcConnectorErrorCode.NO_SUITABLE_DRIVER,
                    "No suitable driver found for " + super.getJdbcConfig().getUrl());
        }
        connection.setAutoCommit(jdbcConfig.isAutoCommit());
        super.setConnection(connection);
        return connection;
    }

    protected Properties buildConnectionProperties() {
        Properties info = new Properties();
        jdbcConfig.getUsername().ifPresent(username -> info.setProperty("user", username));
        jdbcConfig.getPassword().ifPresent(password -> info.setProperty("password", password));
        info.putAll(jdbcConfig.getProperties());
        return info;
    }

    protected Connection createGbase8aConnection(Driver driver, Properties info)
            throws SQLException {
        String url = super.getJdbcConfig().getUrl();
        Optional<String> vcName = Gbase8aVcAwareConnectionHelper.extractVcName(url);
        if (!vcName.isPresent()) {
            return driver.connect(url, info);
        }
        Optional<String> targetDatabase = Gbase8aVcAwareConnectionHelper.extractTargetDatabase(url);
        SQLException lastConnectException = null;
        List<String> bootstrapUrls = Gbase8aVcAwareConnectionHelper.buildBootstrapUrls(url);
        for (String bootstrapUrl : bootstrapUrls) {
            Connection connection = null;
            try {
                connection = driver.connect(bootstrapUrl, info);
                if (connection == null) {
                    continue;
                }
                Gbase8aVcAwareConnectionHelper.initializeSession(
                        connection, vcName.get(), targetDatabase.orElse(null));
                return connection;
            } catch (SQLException ex) {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException closeException) {
                        ex.addSuppressed(closeException);
                    }
                    throw ex;
                }
                lastConnectException = ex;
            }
        }
        if (lastConnectException != null) {
            throw lastConnectException;
        }
        return null;
    }
}

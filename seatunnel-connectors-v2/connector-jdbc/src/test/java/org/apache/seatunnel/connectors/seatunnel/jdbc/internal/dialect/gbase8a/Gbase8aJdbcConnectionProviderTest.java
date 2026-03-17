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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class Gbase8aJdbcConnectionProviderTest {

    @Test
    void shouldBootstrapConnectionAndInitializeVcSession() throws Exception {
        String targetUrl = "jdbc:gbase://127.0.0.1:5258/rot_vcup2_db?vcName=vcup2";
        String bootstrapUrl = "jdbc:gbase://127.0.0.1:5258/information_schema?vcName=vcup2";
        JdbcConnectionConfig config =
                JdbcConnectionConfig.builder()
                        .url(targetUrl)
                        .driverName("com.gbase.jdbc.Driver")
                        .username("root")
                        .password("secret")
                        .build();

        Driver driver = Mockito.mock(Driver.class);
        Connection connection = Mockito.mock(Connection.class);
        Statement statement = Mockito.mock(Statement.class);
        Mockito.when(
                        driver.connect(
                                Mockito.eq(bootstrapUrl), ArgumentMatchers.any(Properties.class)))
                .thenReturn(connection);
        Mockito.when(connection.createStatement()).thenReturn(statement);

        TestGbase8aJdbcConnectionProvider provider =
                new TestGbase8aJdbcConnectionProvider(config, driver);

        Connection established = provider.getOrEstablishConnection();

        Assertions.assertSame(connection, established);
        Mockito.verify(driver)
                .connect(Mockito.eq(bootstrapUrl), ArgumentMatchers.any(Properties.class));
        Mockito.verify(statement).execute("USE VC vcup2");
        Mockito.verify(statement).execute("USE rot_vcup2_db");
        Mockito.verify(connection).setAutoCommit(true);
    }

    @Test
    void shouldFallbackToSecondBootstrapDatabaseWhenFirstFails() throws Exception {
        String targetUrl = "jdbc:gbase://127.0.0.1:5258/rot_vcup2_db?vcName=vcup2";
        String firstBootstrapUrl = "jdbc:gbase://127.0.0.1:5258/information_schema?vcName=vcup2";
        String secondBootstrapUrl = "jdbc:gbase://127.0.0.1:5258/gbase?vcName=vcup2";
        JdbcConnectionConfig config =
                JdbcConnectionConfig.builder()
                        .url(targetUrl)
                        .driverName("com.gbase.jdbc.Driver")
                        .build();

        Driver driver = Mockito.mock(Driver.class);
        Connection connection = Mockito.mock(Connection.class);
        Statement statement = Mockito.mock(Statement.class);
        Mockito.when(
                        driver.connect(
                                Mockito.eq(firstBootstrapUrl),
                                ArgumentMatchers.any(Properties.class)))
                .thenThrow(new SQLException("first bootstrap failed"));
        Mockito.when(
                        driver.connect(
                                Mockito.eq(secondBootstrapUrl),
                                ArgumentMatchers.any(Properties.class)))
                .thenReturn(connection);
        Mockito.when(connection.createStatement()).thenReturn(statement);

        TestGbase8aJdbcConnectionProvider provider =
                new TestGbase8aJdbcConnectionProvider(config, driver);

        Assertions.assertSame(connection, provider.getOrEstablishConnection());
        Mockito.verify(driver)
                .connect(Mockito.eq(firstBootstrapUrl), ArgumentMatchers.any(Properties.class));
        Mockito.verify(driver)
                .connect(Mockito.eq(secondBootstrapUrl), ArgumentMatchers.any(Properties.class));
        Mockito.verify(statement).execute("USE VC vcup2");
        Mockito.verify(statement).execute("USE rot_vcup2_db");
    }

    private static final class TestGbase8aJdbcConnectionProvider
            extends Gbase8aJdbcConnectionProvider {

        private final Driver driver;

        private TestGbase8aJdbcConnectionProvider(JdbcConnectionConfig jdbcConfig, Driver driver) {
            super(jdbcConfig);
            this.driver = driver;
        }

        @Override
        protected Driver getLoadedDriver() {
            return driver;
        }
    }
}

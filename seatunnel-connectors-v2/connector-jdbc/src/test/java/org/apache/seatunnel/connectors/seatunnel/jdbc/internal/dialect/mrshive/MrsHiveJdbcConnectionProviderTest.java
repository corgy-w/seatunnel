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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.mrshive;

import org.apache.seatunnel.connectors.seatunnel.jdbc.config.JdbcConnectionConfig;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.hive.HiveJdbcUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Properties;

class MrsHiveJdbcConnectionProviderTest {

    @Test
    void testKerberosMrsHiveConnectionShouldNotTriggerExtraPreLogin() throws Exception {
        Connection connection = Mockito.mock(Connection.class);
        MrsHiveJdbcConnectionProvider provider =
                new TestingMrsHiveJdbcConnectionProvider(
                        buildKerberosConfig(), connection, null, true);

        try (MockedStatic<HiveJdbcUtils> hiveJdbcUtils = Mockito.mockStatic(HiveJdbcUtils.class)) {
            Assertions.assertSame(connection, provider.getOrEstablishConnection());
            hiveJdbcUtils.verifyNoInteractions();
        }
    }

    @Test
    void testKerberosMrsHiveConnectionShouldUseConfiguredDriver() throws Exception {
        Connection connection = Mockito.mock(Connection.class);
        Driver driver = Mockito.mock(Driver.class);
        ArgumentCaptor<Properties> propertiesCaptor = ArgumentCaptor.forClass(Properties.class);
        String expectedJdbcUrl =
                "jdbc:hive2://localhost:10000/default;principal=hive/localhost@EXAMPLE.COM;"
                        + "user.principal=client@EXAMPLE.COM;user.keytab=/tmp/client.keytab;";
        Mockito.when(driver.connect(Mockito.eq(expectedJdbcUrl), Mockito.any(Properties.class)))
                .thenReturn(connection);
        MrsHiveJdbcConnectionProvider provider =
                new TestingMrsHiveJdbcConnectionProvider(
                        buildKerberosConfig(), null, driver, false);

        Assertions.assertSame(connection, provider.getOrEstablishConnection());
        Mockito.verify(driver).connect(Mockito.eq(expectedJdbcUrl), propertiesCaptor.capture());
        Assertions.assertEquals(
                "client@EXAMPLE.COM", propertiesCaptor.getValue().getProperty("user"));
        Assertions.assertEquals(
                "test-password", propertiesCaptor.getValue().getProperty("password"));
        Assertions.assertEquals("30", propertiesCaptor.getValue().getProperty("socketTimeout"));
        Mockito.verify(connection).setAutoCommit(false);
    }

    @Test
    void testKerberosMrsHiveConnectionShouldThrowWhenDriverReturnsNull() throws Exception {
        Driver driver = Mockito.mock(Driver.class);
        Mockito.when(driver.connect(Mockito.anyString(), Mockito.any(Properties.class)))
                .thenReturn(null);
        MrsHiveJdbcConnectionProvider provider =
                new TestingMrsHiveJdbcConnectionProvider(
                        buildKerberosConfig(), null, driver, false);

        Assertions.assertThrows(JdbcConnectorException.class, provider::getOrEstablishConnection);
    }

    private JdbcConnectionConfig buildKerberosConfig() {
        return JdbcConnectionConfig.builder()
                .url("jdbc:hive2://localhost:10000/default")
                .driverName("org.apache.seatunnel.shade.mrs.org.apache.hive.jdbc.HiveDriver")
                .username("client@EXAMPLE.COM")
                .password("test-password")
                .properties(Collections.singletonMap("socketTimeout", "30"))
                .autoCommit(false)
                .useKerberos(true)
                .kerberosPrincipal("hive/localhost@EXAMPLE.COM")
                .kerberosKeytabPath("/tmp/client.keytab")
                .krb5Path("/tmp/krb5.conf")
                .build();
    }

    private static final class TestingMrsHiveJdbcConnectionProvider
            extends MrsHiveJdbcConnectionProvider {

        private final Driver loadedDriver;
        private final boolean connectionValid;

        private TestingMrsHiveJdbcConnectionProvider(
                JdbcConnectionConfig jdbcConfig,
                Connection connection,
                Driver loadedDriver,
                boolean connectionValid) {
            super(jdbcConfig);
            this.loadedDriver = loadedDriver;
            this.connectionValid = connectionValid;
            setConnection(connection);
        }

        @Override
        public boolean isConnectionValid() throws SQLException {
            return connectionValid;
        }

        @Override
        protected Driver getLoadedDriver() throws SQLException, ClassNotFoundException {
            if (loadedDriver != null) {
                return loadedDriver;
            }
            return super.getLoadedDriver();
        }
    }
}

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
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.hive.HiveJdbcUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.SQLException;

class MrsHiveJdbcConnectionProviderTest {

    @Test
    void testKerberosMrsHiveConnectionShouldNotTriggerExtraPreLogin() throws Exception {
        Connection connection = Mockito.mock(Connection.class);
        MrsHiveJdbcConnectionProvider provider =
                new TestingMrsHiveJdbcConnectionProvider(buildKerberosConfig(), connection);

        try (MockedStatic<HiveJdbcUtils> hiveJdbcUtils = Mockito.mockStatic(HiveJdbcUtils.class)) {
            Assertions.assertSame(connection, provider.getOrEstablishConnection());
            hiveJdbcUtils.verifyNoInteractions();
        }
    }

    private JdbcConnectionConfig buildKerberosConfig() {
        return JdbcConnectionConfig.builder()
                .url("jdbc:hive2://localhost:10000/default")
                .driverName("org.apache.seatunnel.shade.mrs.org.apache.hive.jdbc.HiveDriver")
                .username("client@EXAMPLE.COM")
                .useKerberos(true)
                .kerberosPrincipal("hive/localhost@EXAMPLE.COM")
                .kerberosKeytabPath("/tmp/client.keytab")
                .krb5Path("/tmp/krb5.conf")
                .build();
    }

    private static final class TestingMrsHiveJdbcConnectionProvider
            extends MrsHiveJdbcConnectionProvider {

        private TestingMrsHiveJdbcConnectionProvider(
                JdbcConnectionConfig jdbcConfig, Connection connection) {
            super(jdbcConfig);
            setConnection(connection);
        }

        @Override
        public boolean isConnectionValid() throws SQLException {
            return true;
        }
    }
}

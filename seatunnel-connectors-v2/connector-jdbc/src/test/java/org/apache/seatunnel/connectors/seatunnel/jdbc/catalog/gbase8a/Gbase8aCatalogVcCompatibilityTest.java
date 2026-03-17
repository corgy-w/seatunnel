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

package org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.gbase8a;

import org.apache.seatunnel.common.utils.JdbcUrlUtil;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Gbase8aCatalogVcCompatibilityTest {

    @Test
    void shouldCreateCatalogConnectionThroughBootstrapDatabase() throws Exception {
        String targetUrl = "jdbc:gbase://127.0.0.1:5258/rot_vcup2_db?vcName=vcup2";
        String bootstrapUrl = "jdbc:gbase://127.0.0.1:5258/information_schema?vcName=vcup2";
        Connection connection = Mockito.mock(Connection.class);
        Statement statement = Mockito.mock(Statement.class);
        Mockito.when(connection.createStatement()).thenReturn(statement);

        TestGbase8aCatalog catalog =
                new TestGbase8aCatalog(JdbcUrlUtil.getUrlInfo(targetUrl), bootstrapUrl, connection);

        Connection established = catalog.openForTest(targetUrl);

        Assertions.assertSame(connection, established);
        Assertions.assertEquals(bootstrapUrl, catalog.lastOpenedUrl);
        Mockito.verify(statement).execute("USE VC vcup2");
        Mockito.verify(statement).execute("USE rot_vcup2_db");
    }

    private static final class TestGbase8aCatalog extends Gbase8aCatalog {

        private final String expectedUrl;
        private final Connection connection;
        private String lastOpenedUrl;

        private TestGbase8aCatalog(
                JdbcUrlUtil.UrlInfo urlInfo, String expectedUrl, Connection connection) {
            super("gbase8a", "root", "secret", urlInfo);
            this.expectedUrl = expectedUrl;
            this.connection = connection;
        }

        private Connection openForTest(String url) {
            return super.getConnection(url);
        }

        @Override
        protected Connection openConnection(String url) throws SQLException {
            this.lastOpenedUrl = url;
            Assertions.assertEquals(expectedUrl, url);
            return connection;
        }
    }
}

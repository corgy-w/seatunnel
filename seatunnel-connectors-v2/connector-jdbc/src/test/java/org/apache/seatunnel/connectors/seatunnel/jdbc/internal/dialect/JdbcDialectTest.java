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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect;

import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.converter.JdbcRowConverter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.source.StringRangeSplitDecision;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class JdbcDialectTest {

    @Test
    public void testDefaultStringRangeSplitValidationIsUnsafe() throws SQLException {
        JdbcDialect dialect = new UnsupportedStringRangeSplitDialect();

        StringRangeSplitDecision decision = dialect.validateStringRangeSplit(null, null, "id", 256);

        assertFalse(decision.isSafe());
    }

    private static class UnsupportedStringRangeSplitDialect implements JdbcDialect {

        @Override
        public String dialectName() {
            return "test";
        }

        @Override
        public JdbcRowConverter getRowConverter() {
            return null;
        }

        @Override
        public JdbcDialectTypeMapper getJdbcDialectTypeMapper() {
            return null;
        }

        @Override
        public Optional<String> getUpsertStatement(
                String database,
                String tableName,
                String[] fieldNames,
                String[] uniqueKeyFields,
                boolean isPrimaryKeyUpdated) {
            return Optional.empty();
        }
    }
}

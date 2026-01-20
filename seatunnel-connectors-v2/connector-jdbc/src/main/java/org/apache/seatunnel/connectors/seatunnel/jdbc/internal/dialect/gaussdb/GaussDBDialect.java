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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.gaussdb;

import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.converter.JdbcRowConverter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.JdbcDialectTypeMapper;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.psql.PostgresDialect;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class GaussDBDialect extends PostgresDialect {
    private static final long serialVersionUID = -5834746193472465210L;

    /** e.g. "mysql" / "postgres" / null */
    private final String compatibleMode;

    public GaussDBDialect() {
        super();
        this.compatibleMode = null;
    }

    public GaussDBDialect(String compatibleMode) {
        super();
        this.compatibleMode = compatibleMode;
    }

    private boolean isMySqlMode() {
        return "mysql".equalsIgnoreCase(compatibleMode);
    }

    @Override
    public String dialectName() {
        return DatabaseIdentifier.GAUSSDB;
    }

    @Override
    public JdbcRowConverter getRowConverter() {
        return new GaussDBJdbcRowConverter();
    }

    @Override
    public JdbcDialectTypeMapper getJdbcDialectTypeMapper() {
        return new GaussDBTypeMapper();
    }

    @Override
    public String hashModForField(String nativeType, String fieldName, int mod) {
        String quoteFieldName = quoteIdentifier(fieldName);
        if (StringUtils.isNotBlank(nativeType)) {
            quoteFieldName = convertType(quoteFieldName, nativeType);
        }

        if (isMySqlMode()) {
            return "(ABS(CRC32(" + quoteFieldName + ")) % " + mod + ")";
        }

        return "(ABS(HASHTEXT(" + quoteFieldName + ")) % " + mod + ")";
    }

    @Override
    public Optional<String> getUpsertStatement(
            String database,
            String tableName,
            String[] fieldNames,
            String[] uniqueKeyFields,
            boolean isPrimaryKeyUpdated) {

        final String insertSql = getInsertIntoStatement(database, tableName, fieldNames);

        final Set<String> uniqueKeys = new HashSet<>(Arrays.asList(uniqueKeyFields));

        if (isMySqlMode()) {
            String updateClause =
                    Arrays.stream(fieldNames)
                            .filter(f -> isPrimaryKeyUpdated || !uniqueKeys.contains(f))
                            .map(f -> quoteIdentifier(f) + "=VALUES(" + quoteIdentifier(f) + ")")
                            .collect(Collectors.joining(", "));

            if (StringUtils.isBlank(updateClause)) {
                String noOpField = uniqueKeyFields.length > 0 ? uniqueKeyFields[0] : fieldNames[0];
                updateClause = quoteIdentifier(noOpField) + "=" + quoteIdentifier(noOpField);
            }

            String upsertSql =
                    String.format("%s ON DUPLICATE KEY UPDATE %s", insertSql, updateClause);
            return Optional.of(upsertSql);
        }

        String uniqueColumns =
                Arrays.stream(uniqueKeyFields)
                        .map(this::quoteIdentifier)
                        .collect(Collectors.joining(", "));

        String updateClause =
                Arrays.stream(fieldNames)
                        .filter(f -> isPrimaryKeyUpdated || !uniqueKeys.contains(f))
                        .map(f -> quoteIdentifier(f) + "=EXCLUDED." + quoteIdentifier(f))
                        .collect(Collectors.joining(", "));

        String upsertSql;
        if (StringUtils.isBlank(updateClause)) {
            upsertSql = String.format("%s ON CONFLICT (%s) DO NOTHING", insertSql, uniqueColumns);
        } else {
            upsertSql =
                    String.format(
                            "%s ON CONFLICT (%s) DO UPDATE SET %s",
                            insertSql, uniqueColumns, updateClause);
        }

        return Optional.of(upsertSql);
    }
}

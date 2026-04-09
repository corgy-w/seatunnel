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

package org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.oceanbase;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.oracle.OracleCreateTableSqlBuilder;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.oracle.OracleTypeConverter;

import org.apache.commons.lang3.StringUtils;

public class OceanBaseOracleCreateTableSqlBuilder extends OracleCreateTableSqlBuilder {

    public OceanBaseOracleCreateTableSqlBuilder(CatalogTable catalogTable, boolean createIndex) {
        super(catalogTable, createIndex);
    }

    @Override
    protected String buildColumnSql(Column column) {
        StringBuilder columnSql = new StringBuilder();
        columnSql.append("\"").append(column.getName()).append("\" ");

        String columnType = null;
        if (column.getSinkType() != null) {
            columnType = column.getSinkType();
        } else if (StringUtils.isNotBlank(column.getSourceType())) {
            if (StringUtils.equalsIgnoreCase(DatabaseIdentifier.OCENABASE, sourceCatalogName)) {
                String sourceType = column.getSourceType();
                columnType = mapUnsupportedOracleType(sourceType, false);
                if (columnType == null && isOceanBaseOracleCompatibleType(sourceType)) {
                    columnType = sourceType;
                }
            } else if (StringUtils.equalsIgnoreCase(DatabaseIdentifier.ORACLE, sourceCatalogName)) {
                columnType = mapUnsupportedOracleType(column.getSourceType(), true);
            }
        }

        if (columnType == null) {
            columnType = OracleTypeConverter.INSTANCE.reconvert(column).getColumnType();
        }

        columnSql.append(columnType);

        if (!column.isNullable()) {
            columnSql.append(" NOT NULL");
        }

        return columnSql.toString();
    }

    private String mapUnsupportedOracleType(String sourceType, boolean fallbackToSourceType) {
        // handle OceanBase Oracle compatible mode unsupported types, please refer
        // https://www.oceanbase.com/docs/enterprise-oceanbase-database-cn-10000000000355002
        // and https://www.oceanbase.com/docs/enterprise-oms-doc-cn-1000000002530110
        switch (sourceType.toUpperCase()) {
            case OracleTypeConverter.ORACLE_LONG:
                return OracleTypeConverter.ORACLE_CLOB;
            case OracleTypeConverter.ORACLE_LONG_RAW:
            case OracleTypeConverter.ORACLE_BFILE:
                return OracleTypeConverter.ORACLE_BLOB;
            case OracleTypeConverter.ORACLE_NCLOB:
                // set max length to 32767, which is the maximum length supported by
                // OceanBase
                return OracleTypeConverter.ORACLE_NVARCHAR2 + "(32767)";
            case OracleTypeConverter.ORACLE_REAL:
                return OracleTypeConverter.ORACLE_FLOAT;
            default:
                return fallbackToSourceType ? sourceType : null;
        }
    }

    private boolean isOceanBaseOracleCompatibleType(String sourceType) {
        String upperSourceType = sourceType.toUpperCase().trim();
        if (upperSourceType.startsWith(OracleTypeConverter.ORACLE_TIMESTAMP)
                || upperSourceType.startsWith(OracleTypeConverter.ORACLE_INTERVAL)) {
            return true;
        }

        String baseType = upperSourceType.split("[\\s(]", 2)[0];
        switch (baseType) {
            case OracleTypeConverter.ORACLE_INTEGER:
            case OracleTypeConverter.ORACLE_NUMBER:
            case OracleTypeConverter.ORACLE_FLOAT:
            case OracleTypeConverter.ORACLE_BINARY_FLOAT:
            case OracleTypeConverter.ORACLE_BINARY_DOUBLE:
            case OracleTypeConverter.ORACLE_CHAR:
            case OracleTypeConverter.ORACLE_NCHAR:
            case OracleTypeConverter.ORACLE_VARCHAR2:
            case OracleTypeConverter.ORACLE_NVARCHAR2:
            case OracleTypeConverter.ORACLE_DATE:
            case OracleTypeConverter.ORACLE_RAW:
            case OracleTypeConverter.ORACLE_CLOB:
            case OracleTypeConverter.ORACLE_BLOB:
                return true;
            default:
                return false;
        }
    }
}

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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.oracle;

import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SqlType;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.converter.AbstractJdbcRowConverter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;

import javax.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import static org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.oracle.OracleTypeConverter.ORACLE_BLOB;
import static org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.oracle.OracleTypeConverter.ORACLE_CHAR;
import static org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.oracle.OracleTypeConverter.ORACLE_CLOB;
import static org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.oracle.OracleTypeConverter.ORACLE_NCHAR;
import static org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.oracle.OracleTypeConverter.ORACLE_NCLOB;
import static org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.oracle.OracleTypeConverter.ORACLE_NVARCHAR2;
import static org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.oracle.OracleTypeConverter.ORACLE_VARCHAR;
import static org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.oracle.OracleTypeConverter.ORACLE_VARCHAR2;

public class OracleJdbcRowConverter extends AbstractJdbcRowConverter {

    @Override
    public String converterName() {
        return DatabaseIdentifier.ORACLE;
    }

    @Override
    protected void setNullToStatementByDataType(
            PreparedStatement statement,
            SeaTunnelDataType<?> seaTunnelDataType,
            int statementIndex,
            @Nullable String sourceType)
            throws SQLException {
        if (ORACLE_CLOB.equals(sourceType)) {
            statement.setNull(statementIndex, Types.CLOB);
            return;
        }
        if (ORACLE_NCLOB.equals(sourceType)) {
            statement.setNull(statementIndex, Types.NCLOB);
            return;
        }
        if (ORACLE_BLOB.equals(sourceType)) {
            statement.setNull(statementIndex, Types.BLOB);
            return;
        }
        if (ORACLE_CHAR.equals(sourceType)
                || ORACLE_NCHAR.equals(sourceType)
                || ORACLE_VARCHAR.equals(sourceType)
                || ORACLE_VARCHAR2.equals(sourceType)
                || ORACLE_NVARCHAR2.equals(sourceType)) {
            statement.setNull(statementIndex, Types.VARCHAR);
            return;
        }

        switch (seaTunnelDataType.getSqlType()) {
            case STRING:
                statement.setNull(statementIndex, Types.VARCHAR);
                break;
            case BOOLEAN:
                // Oracle has no native BOOLEAN column type in ordinary SQL tables.
                statement.setNull(statementIndex, Types.INTEGER);
                break;
            case TINYINT:
                statement.setNull(statementIndex, Types.TINYINT);
                break;
            case SMALLINT:
                statement.setNull(statementIndex, Types.SMALLINT);
                break;
            case INT:
                statement.setNull(statementIndex, Types.INTEGER);
                break;
            case BIGINT:
                statement.setNull(statementIndex, Types.BIGINT);
                break;
            case FLOAT:
                statement.setNull(statementIndex, Types.FLOAT);
                break;
            case DOUBLE:
                statement.setNull(statementIndex, Types.DOUBLE);
                break;
            case DECIMAL:
                statement.setNull(statementIndex, Types.DECIMAL);
                break;
            case DATE:
                statement.setNull(statementIndex, Types.DATE);
                break;
            case TIME:
                statement.setNull(statementIndex, Types.TIME);
                break;
            case TIMESTAMP:
                statement.setNull(statementIndex, Types.TIMESTAMP);
                break;
            case BYTES:
                statement.setNull(statementIndex, Types.BINARY);
                break;
            case NULL:
                statement.setNull(statementIndex, Types.NULL);
                break;
            case ARRAY:
                statement.setNull(statementIndex, Types.ARRAY);
                break;
            case MAP:
            case ROW:
            default:
                super.setNullToStatementByDataType(
                        statement, seaTunnelDataType, statementIndex, sourceType);
        }
    }

    @Override
    protected void setValueToStatementByDataType(
            Object value,
            PreparedStatement statement,
            SeaTunnelDataType<?> seaTunnelDataType,
            int statementIndex,
            @Nullable String sourceType)
            throws SQLException {
        if (seaTunnelDataType.getSqlType().equals(SqlType.BYTES)) {
            if (ORACLE_BLOB.equals(sourceType)) {
                byte[] bytes = (byte[]) value;
                statement.setBinaryStream(
                        statementIndex, new ByteArrayInputStream(bytes), bytes.length);
            } else {
                statement.setBytes(statementIndex, (byte[]) value);
            }
        } else if (seaTunnelDataType.getSqlType().equals(SqlType.STRING)) {
            // Handle CLOB/NCLOB types to avoid ORA-01461 in batch mode
            // Oracle JDBC driver may bind setString() as LONG when writing to CLOB columns,
            // which causes "can bind a LONG value only for insert into a LONG column" error
            if (ORACLE_CLOB.equals(sourceType) || ORACLE_NCLOB.equals(sourceType)) {
                // Use setCharacterStream for CLOB/NCLOB columns to ensure correct binding
                String strValue = (String) value;
                statement.setCharacterStream(
                        statementIndex, new StringReader(strValue), strValue.length());
            } else {
                statement.setString(statementIndex, (String) value);
            }
        } else {
            super.setValueToStatementByDataType(
                    value, statement, seaTunnelDataType, statementIndex, sourceType);
        }
    }
}

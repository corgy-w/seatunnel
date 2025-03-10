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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.starrocks;

import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.api.table.converter.TypeConverter;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.DecimalType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.PrimitiveByteArrayType;
import org.apache.seatunnel.common.exception.CommonError;
import org.apache.seatunnel.connectors.seatunnel.common.source.TypeDefineUtils;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;

import com.google.auto.service.AutoService;
import com.google.common.base.Preconditions;
import com.mysql.cj.MysqlType;
import lombok.extern.slf4j.Slf4j;

// reference https://dev.mysql.com/doc/refman/8.0/en/data-types.html
@Slf4j
@AutoService(TypeConverter.class)
public class JdbcStarRocksTypeConverter implements TypeConverter<BasicTypeDefine<MysqlType>> {

    // ============================data types=====================
    static final String STARROCKS_NULL = "NULL";
    static final String STARROCKS_BIT = "BIT";
    static final String STARROCKS_BIT_UNSIGNED = "BIT UNSIGNED";

    // -------------------------number----------------------------
    static final String STARROCKS_TINYINT = "TINYINT";
    static final String STARROCKS_TINYINT_UNSIGNED = "TINYINT UNSIGNED";
    static final String STARROCKS_SMALLINT = "SMALLINT";
    static final String STARROCKS_SMALLINT_UNSIGNED = "SMALLINT UNSIGNED";
    static final String STARROCKS_MEDIUMINT = "MEDIUMINT";
    static final String STARROCKS_MEDIUMINT_UNSIGNED = "MEDIUMINT UNSIGNED";
    static final String STARROCKS_INT = "INT";
    static final String STARROCKS_INT_UNSIGNED = "INT UNSIGNED";
    static final String STARROCKS_INTEGER = "INTEGER";
    static final String STARROCKS_INTEGER_UNSIGNED = "INTEGER UNSIGNED";
    static final String STARROCKS_BIGINT = "BIGINT";
    static final String STARROCKS_BIGINT_UNSIGNED = "BIGINT UNSIGNED";
    static final String STARROCKS_DECIMAL = "DECIMAL";
    static final String STARROCKS_DECIMAL_UNSIGNED = "DECIMAL UNSIGNED";
    static final String STARROCKS_FLOAT = "FLOAT";
    static final String STARROCKS_FLOAT_UNSIGNED = "FLOAT UNSIGNED";
    static final String STARROCKS_DOUBLE = "DOUBLE";
    static final String STARROCKS_DOUBLE_UNSIGNED = "DOUBLE UNSIGNED";

    // -------------------------string----------------------------
    public static final String STARROCKS_CHAR = "CHAR";
    public static final String STARROCKS_VARCHAR = "VARCHAR";
    static final String STARROCKS_TINYTEXT = "TINYTEXT";
    static final String STARROCKS_MEDIUMTEXT = "MEDIUMTEXT";
    static final String STARROCKS_TEXT = "TEXT";
    static final String STARROCKS_LONGTEXT = "LONGTEXT";
    static final String STARROCKS_JSON = "JSON";
    static final String STARROCKS_ENUM = "ENUM";

    // ------------------------------time-------------------------
    static final String STARROCKS_DATE = "DATE";
    public static final String STARROCKS_DATETIME = "DATETIME";
    public static final String STARROCKS_TIME = "TIME";
    public static final String STARROCKS_TIMESTAMP = "TIMESTAMP";
    static final String STARROCKS_YEAR = "YEAR";
    static final String STARROCKS_YEAR_UNSIGNED = "YEAR UNSIGNED";

    // ------------------------------blob-------------------------
    static final String STARROCKS_TINYBLOB = "TINYBLOB";
    static final String STARROCKS_MEDIUMBLOB = "MEDIUMBLOB";
    static final String STARROCKS_BLOB = "BLOB";
    static final String STARROCKS_LONGBLOB = "LONGBLOB";
    static final String STARROCKS_BINARY = "BINARY";
    static final String STARROCKS_VARBINARY = "VARBINARY";
    static final String STARROCKS_GEOMETRY = "GEOMETRY";

    public static final int DEFAULT_PRECISION = 38;
    public static final int MAX_PRECISION = 65;
    public static final int DEFAULT_SCALE = 18;
    public static final int MAX_SCALE = 30;
    public static final int MAX_TIME_SCALE = 6;
    public static final int MAX_TIMESTAMP_SCALE = 6;
    public static final long POWER_2_8 = (long) Math.pow(2, 8);
    public static final long POWER_2_16 = (long) Math.pow(2, 16);
    public static final long POWER_2_24 = (long) Math.pow(2, 24);
    public static final long POWER_2_32 = (long) Math.pow(2, 32);
    public static final long MAX_VARBINARY_LENGTH = POWER_2_16 - 4;
    public static final JdbcStarRocksTypeConverter INSTANCE = new JdbcStarRocksTypeConverter();

    @Override
    public String identifier() {
        return DatabaseIdentifier.MYSQL;
    }

    @Override
    public Column convert(BasicTypeDefine typeDefine) {
        PhysicalColumn.PhysicalColumnBuilder builder =
                PhysicalColumn.builder()
                        .name(typeDefine.getName())
                        .sourceType(typeDefine.getColumnType())
                        .nullable(typeDefine.isNullable())
                        .defaultValue(typeDefine.getDefaultValue())
                        .comment(typeDefine.getComment());

        String mysqlDataType = typeDefine.getDataType().toUpperCase();
        if (mysqlDataType.endsWith("ZEROFILL")) {
            mysqlDataType =
                    mysqlDataType.substring(0, mysqlDataType.length() - "ZEROFILL".length()).trim();
        }
        if (typeDefine.isUnsigned() && !(mysqlDataType.endsWith(" UNSIGNED"))) {
            mysqlDataType = mysqlDataType + " UNSIGNED";
        }
        switch (mysqlDataType) {
            case STARROCKS_NULL:
                builder.dataType(BasicType.VOID_TYPE);
                break;
            case STARROCKS_BIT:
            case STARROCKS_BIT_UNSIGNED:
                if (typeDefine.getLength() == null || typeDefine.getLength() <= 0) {
                    builder.dataType(BasicType.BOOLEAN_TYPE);
                } else if (typeDefine.getLength() == 1) {
                    builder.dataType(BasicType.BOOLEAN_TYPE);
                } else {
                    builder.dataType(PrimitiveByteArrayType.INSTANCE);
                    // BIT(M) -> BYTE(M/8)
                    long byteLength = typeDefine.getLength() / 8;
                    byteLength += typeDefine.getLength() % 8 > 0 ? 1 : 0;
                    builder.columnLength(byteLength);
                }
                break;
            case STARROCKS_TINYINT:
                if (typeDefine.getColumnType().equalsIgnoreCase("tinyint(1)")) {
                    builder.dataType(BasicType.BOOLEAN_TYPE);
                } else {
                    builder.dataType(BasicType.BYTE_TYPE);
                }
                break;
            case STARROCKS_TINYINT_UNSIGNED:
            case STARROCKS_SMALLINT:
                builder.dataType(BasicType.SHORT_TYPE);
                break;
            case STARROCKS_SMALLINT_UNSIGNED:
            case STARROCKS_MEDIUMINT:
            case STARROCKS_MEDIUMINT_UNSIGNED:
            case STARROCKS_INT:
            case STARROCKS_INTEGER:
            case STARROCKS_YEAR:
            case STARROCKS_YEAR_UNSIGNED:
                builder.dataType(BasicType.INT_TYPE);
                break;
            case STARROCKS_INT_UNSIGNED:
            case STARROCKS_INTEGER_UNSIGNED:
            case STARROCKS_BIGINT:
                builder.dataType(BasicType.LONG_TYPE);
                break;
            case STARROCKS_BIGINT_UNSIGNED:
                DecimalType intDecimalType = new DecimalType(20, 0);
                builder.dataType(intDecimalType);
                builder.columnLength(Long.valueOf(intDecimalType.getPrecision()));
                builder.scale(intDecimalType.getScale());
                break;
            case STARROCKS_FLOAT:
                builder.dataType(BasicType.FLOAT_TYPE);
                break;
            case STARROCKS_FLOAT_UNSIGNED:
                log.warn("{} will probably cause value overflow.", STARROCKS_FLOAT_UNSIGNED);
                builder.dataType(BasicType.FLOAT_TYPE);
                break;
            case STARROCKS_DOUBLE:
                builder.dataType(BasicType.DOUBLE_TYPE);
                break;
            case STARROCKS_DOUBLE_UNSIGNED:
                log.warn("{} will probably cause value overflow.", STARROCKS_DOUBLE_UNSIGNED);
                builder.dataType(BasicType.DOUBLE_TYPE);
                break;
            case STARROCKS_DECIMAL:
                Preconditions.checkArgument(typeDefine.getPrecision() > 0);

                DecimalType decimalType;
                if (typeDefine.getPrecision() > DEFAULT_PRECISION) {
                    log.warn("{} will probably cause value overflow.", STARROCKS_DECIMAL);
                    decimalType = new DecimalType(DEFAULT_PRECISION, DEFAULT_SCALE);
                } else {
                    decimalType =
                            new DecimalType(
                                    typeDefine.getPrecision().intValue(),
                                    typeDefine.getScale() == null
                                            ? 0
                                            : typeDefine.getScale().intValue());
                }
                builder.dataType(decimalType);
                builder.columnLength(Long.valueOf(decimalType.getPrecision()));
                builder.scale(decimalType.getScale());
                break;
            case STARROCKS_DECIMAL_UNSIGNED:
                Preconditions.checkArgument(typeDefine.getPrecision() > 0);

                log.warn("{} will probably cause value overflow.", STARROCKS_DECIMAL_UNSIGNED);
                DecimalType decimalUnsignedType =
                        new DecimalType(
                                typeDefine.getPrecision().intValue() + 1,
                                typeDefine.getScale() == null
                                        ? 0
                                        : typeDefine.getScale().intValue());
                builder.dataType(decimalUnsignedType);
                builder.columnLength(Long.valueOf(decimalUnsignedType.getPrecision()));
                builder.scale(decimalUnsignedType.getScale());
                break;
            case STARROCKS_ENUM:
                builder.dataType(BasicType.STRING_TYPE);
                if (typeDefine.getLength() == null || typeDefine.getLength() <= 0) {
                    builder.columnLength(100L);
                } else {
                    builder.columnLength(typeDefine.getLength());
                }
                break;
            case STARROCKS_CHAR:
            case STARROCKS_VARCHAR:
                if (typeDefine.getLength() == null || typeDefine.getLength() <= 0) {
                    builder.columnLength(TypeDefineUtils.charTo4ByteLength(1L));
                } else {
                    builder.columnLength(typeDefine.getLength());
                }
                builder.dataType(BasicType.STRING_TYPE);
                break;
            case STARROCKS_TINYTEXT:
                builder.dataType(BasicType.STRING_TYPE);
                builder.columnLength(POWER_2_8 - 1);
                break;
            case STARROCKS_TEXT:
                builder.dataType(BasicType.STRING_TYPE);
                builder.columnLength(POWER_2_16 - 1);
                break;
            case STARROCKS_MEDIUMTEXT:
                builder.dataType(BasicType.STRING_TYPE);
                builder.columnLength(POWER_2_24 - 1);
                break;
            case STARROCKS_LONGTEXT:
                builder.dataType(BasicType.STRING_TYPE);
                builder.columnLength(POWER_2_32 - 1);
                break;
            case STARROCKS_JSON:
                builder.dataType(BasicType.STRING_TYPE);
                break;
            case STARROCKS_BINARY:
            case STARROCKS_VARBINARY:
                if (typeDefine.getLength() == null || typeDefine.getLength() <= 0) {
                    builder.columnLength(1L);
                } else {
                    builder.columnLength(typeDefine.getLength());
                }
                builder.dataType(PrimitiveByteArrayType.INSTANCE);
                break;
            case STARROCKS_TINYBLOB:
                builder.dataType(PrimitiveByteArrayType.INSTANCE);
                builder.columnLength(POWER_2_8 - 1);
                break;
            case STARROCKS_BLOB:
                builder.dataType(PrimitiveByteArrayType.INSTANCE);
                builder.columnLength(POWER_2_16 - 1);
                break;
            case STARROCKS_MEDIUMBLOB:
                builder.dataType(PrimitiveByteArrayType.INSTANCE);
                builder.columnLength(POWER_2_24 - 1);
                break;
            case STARROCKS_LONGBLOB:
                builder.dataType(PrimitiveByteArrayType.INSTANCE);
                builder.columnLength(POWER_2_32 - 1);
                break;
            case STARROCKS_GEOMETRY:
                builder.dataType(PrimitiveByteArrayType.INSTANCE);
                break;
            case STARROCKS_DATE:
                builder.dataType(LocalTimeType.LOCAL_DATE_TYPE);
                break;
            case STARROCKS_TIME:
                builder.dataType(LocalTimeType.LOCAL_TIME_TYPE);
                builder.scale(typeDefine.getScale());
                break;
            case STARROCKS_DATETIME:
            case STARROCKS_TIMESTAMP:
                builder.dataType(LocalTimeType.LOCAL_DATE_TIME_TYPE);
                builder.scale(typeDefine.getScale());
                break;
            default:
                throw CommonError.convertToSeaTunnelTypeError(
                        DatabaseIdentifier.MYSQL, mysqlDataType, typeDefine.getName());
        }
        return builder.build();
    }

    @Override
    public BasicTypeDefine<MysqlType> reconvert(Column column) {
        BasicTypeDefine.BasicTypeDefineBuilder builder =
                BasicTypeDefine.<MysqlType>builder()
                        .name(column.getName())
                        .nullable(column.isNullable())
                        .comment(column.getComment())
                        .defaultValue(column.getDefaultValue());
        switch (column.getDataType().getSqlType()) {
            case NULL:
                builder.nativeType(MysqlType.NULL);
                builder.columnType(STARROCKS_NULL);
                builder.dataType(STARROCKS_NULL);
                break;
            case BOOLEAN:
                builder.nativeType(MysqlType.BOOLEAN);
                builder.columnType(String.format("%s(%s)", STARROCKS_TINYINT, 1));
                builder.dataType(STARROCKS_TINYINT);
                builder.length(1L);
                break;
            case TINYINT:
                builder.nativeType(MysqlType.TINYINT);
                builder.columnType(STARROCKS_TINYINT);
                builder.dataType(STARROCKS_TINYINT);
                break;
            case SMALLINT:
                builder.nativeType(MysqlType.SMALLINT);
                builder.columnType(STARROCKS_SMALLINT);
                builder.dataType(STARROCKS_SMALLINT);
                break;
            case INT:
                builder.nativeType(MysqlType.INT);
                builder.columnType(STARROCKS_INT);
                builder.dataType(STARROCKS_INT);
                break;
            case BIGINT:
                builder.nativeType(MysqlType.BIGINT);
                builder.columnType(STARROCKS_BIGINT);
                builder.dataType(STARROCKS_BIGINT);
                break;
            case FLOAT:
                builder.nativeType(MysqlType.FLOAT);
                builder.columnType(STARROCKS_FLOAT);
                builder.dataType(STARROCKS_FLOAT);
                break;
            case DOUBLE:
                builder.nativeType(MysqlType.DOUBLE);
                builder.columnType(STARROCKS_DOUBLE);
                builder.dataType(STARROCKS_DOUBLE);
                break;
            case DECIMAL:
                DecimalType decimalType = (DecimalType) column.getDataType();
                long precision = decimalType.getPrecision();
                int scale = decimalType.getScale();
                if (precision <= 0) {
                    precision = DEFAULT_PRECISION;
                    scale = DEFAULT_SCALE;
                    log.warn(
                            "The decimal column {} type decimal({},{}) is out of range, "
                                    + "which is precision less than 0, "
                                    + "it will be converted to decimal({},{})",
                            column.getName(),
                            decimalType.getPrecision(),
                            decimalType.getScale(),
                            precision,
                            scale);
                } else if (precision > MAX_PRECISION) {
                    scale = (int) Math.max(0, scale - (precision - MAX_PRECISION));
                    precision = MAX_PRECISION;
                    log.warn(
                            "The decimal column {} type decimal({},{}) is out of range, "
                                    + "which exceeds the maximum precision of {}, "
                                    + "it will be converted to decimal({},{})",
                            column.getName(),
                            decimalType.getPrecision(),
                            decimalType.getScale(),
                            MAX_PRECISION,
                            precision,
                            scale);
                }
                if (scale < 0) {
                    scale = 0;
                    log.warn(
                            "The decimal column {} type decimal({},{}) is out of range, "
                                    + "which is scale less than 0, "
                                    + "it will be converted to decimal({},{})",
                            column.getName(),
                            decimalType.getPrecision(),
                            decimalType.getScale(),
                            precision,
                            scale);
                } else if (scale > MAX_SCALE) {
                    scale = MAX_SCALE;
                    log.warn(
                            "The decimal column {} type decimal({},{}) is out of range, "
                                    + "which exceeds the maximum scale of {}, "
                                    + "it will be converted to decimal({},{})",
                            column.getName(),
                            decimalType.getPrecision(),
                            decimalType.getScale(),
                            MAX_SCALE,
                            precision,
                            scale);
                }

                builder.nativeType(MysqlType.DECIMAL);
                builder.columnType(String.format("%s(%s,%s)", STARROCKS_DECIMAL, precision, scale));
                builder.dataType(STARROCKS_DECIMAL);
                builder.precision(precision);
                builder.scale(scale);
                break;
            case BYTES:
                if (column.getColumnLength() == null || column.getColumnLength() <= 0) {
                    builder.nativeType(MysqlType.VARBINARY);
                    builder.columnType(
                            String.format("%s(%s)", STARROCKS_VARBINARY, MAX_VARBINARY_LENGTH / 2));
                    builder.dataType(STARROCKS_VARBINARY);
                } else if (column.getColumnLength() < MAX_VARBINARY_LENGTH) {
                    builder.nativeType(MysqlType.VARBINARY);
                    builder.columnType(
                            String.format("%s(%s)", STARROCKS_VARBINARY, column.getColumnLength()));
                    builder.dataType(STARROCKS_VARBINARY);
                } else if (column.getColumnLength() < POWER_2_24) {
                    builder.nativeType(MysqlType.MEDIUMBLOB);
                    builder.columnType(STARROCKS_MEDIUMBLOB);
                    builder.dataType(STARROCKS_MEDIUMBLOB);
                } else {
                    builder.nativeType(MysqlType.LONGBLOB);
                    builder.columnType(STARROCKS_LONGBLOB);
                    builder.dataType(STARROCKS_LONGBLOB);
                }
                break;
            case STRING:
                if (column.getColumnLength() == null || column.getColumnLength() <= 0) {
                    builder.nativeType(MysqlType.LONGTEXT);
                    builder.columnType(STARROCKS_LONGTEXT);
                    builder.dataType(STARROCKS_LONGTEXT);
                } else if (column.getColumnLength() < POWER_2_8) {
                    builder.nativeType(MysqlType.VARCHAR);
                    builder.columnType(
                            String.format("%s(%s)", STARROCKS_VARCHAR, column.getColumnLength()));
                    builder.dataType(STARROCKS_VARCHAR);
                } else if (column.getColumnLength() < POWER_2_16) {
                    builder.nativeType(MysqlType.TEXT);
                    builder.columnType(STARROCKS_TEXT);
                    builder.dataType(STARROCKS_TEXT);
                } else if (column.getColumnLength() < POWER_2_24) {
                    builder.nativeType(MysqlType.MEDIUMTEXT);
                    builder.columnType(STARROCKS_MEDIUMTEXT);
                    builder.dataType(STARROCKS_MEDIUMTEXT);
                } else {
                    builder.nativeType(MysqlType.LONGTEXT);
                    builder.columnType(STARROCKS_LONGTEXT);
                    builder.dataType(STARROCKS_LONGTEXT);
                }
                break;
            case DATE:
                builder.nativeType(MysqlType.DATE);
                builder.columnType(STARROCKS_DATE);
                builder.dataType(STARROCKS_DATE);
                break;
            case TIME:
                builder.nativeType(MysqlType.TIME);
                builder.dataType(STARROCKS_TIME);
                if (column.getScale() != null && column.getScale() > 0) {
                    int timeScale = column.getScale();
                    if (timeScale > MAX_TIME_SCALE) {
                        timeScale = MAX_TIME_SCALE;
                        log.warn(
                                "The time column {} type time({}) is out of range, "
                                        + "which exceeds the maximum scale of {}, "
                                        + "it will be converted to time({})",
                                column.getName(),
                                column.getScale(),
                                MAX_SCALE,
                                timeScale);
                    }
                    builder.columnType(String.format("%s(%s)", STARROCKS_TIME, timeScale));
                    builder.scale(timeScale);
                } else {
                    builder.columnType(STARROCKS_TIME);
                }
                break;
            case TIMESTAMP:
                builder.nativeType(MysqlType.DATETIME);
                builder.dataType(STARROCKS_DATETIME);
                if (column.getScale() != null && column.getScale() > 0) {
                    int timestampScale = column.getScale();
                    if (timestampScale > MAX_TIMESTAMP_SCALE) {
                        timestampScale = MAX_TIMESTAMP_SCALE;
                        log.warn(
                                "The timestamp column {} type timestamp({}) is out of range, "
                                        + "which exceeds the maximum scale of {}, "
                                        + "it will be converted to timestamp({})",
                                column.getName(),
                                column.getScale(),
                                MAX_TIMESTAMP_SCALE,
                                timestampScale);
                    }
                    builder.columnType(String.format("%s(%s)", STARROCKS_DATETIME, timestampScale));
                    builder.scale(timestampScale);
                } else {
                    builder.columnType(STARROCKS_DATETIME);
                }
                break;
            default:
                throw CommonError.convertToConnectorTypeError(
                        DatabaseIdentifier.MYSQL,
                        column.getDataType().getSqlType().name(),
                        column.getName());
        }

        return builder.build();
    }
}

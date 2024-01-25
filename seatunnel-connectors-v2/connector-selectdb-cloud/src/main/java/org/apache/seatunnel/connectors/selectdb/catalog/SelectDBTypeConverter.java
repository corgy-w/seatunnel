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

package org.apache.seatunnel.connectors.selectdb.catalog;

import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.api.table.converter.TypeConverter;
import org.apache.seatunnel.api.table.type.ArrayType;
import org.apache.seatunnel.api.table.type.DecimalType;
import org.apache.seatunnel.common.exception.CommonError;

import com.google.auto.service.AutoService;
import com.mysql.cj.MysqlType;
import lombok.extern.slf4j.Slf4j;

// reference https://docs.selectdb.com/docs/cloud/sql-manual/data-types/INT
@Slf4j
@AutoService(TypeConverter.class)
public class SelectDBTypeConverter implements TypeConverter<BasicTypeDefine> {
    private static final String IDENTIFIER = "SelectDBCloud";
    private static final String SELECTDB_BOOLEAN = "BOOLEAN";
    private static final String SELECTDB_TINYINT = "TINYINT";
    private static final String SELECTDB_SMALLINT = "SMALLINT";
    private static final String SELECTDB_INT = "INT";
    private static final String SELECTDB_BIGINT = "BIGINT";
    private static final String SELECTDB_FLOAT = "FLOAT";
    private static final String SELECTDB_DOUBLE = "DOUBLE";
    private static final String SELECTDB_DECIMALV3 = "Decimalv3";
    private static final String SELECTDB_STRING = "STRING";
    private static final String SELECTDB_VARCHAR = "VARCHAR";
    private static final String SELECTDB_DATE = "DATE";
    private static final String SELECTDB_DATETIMEV2 = "DATETIMEV2";
    private static final String SELECTDB_JSON = "JSON";
    private static final String SELECTDB_ARRAY = "ARRAY";
    private static final long MAX_PRECISION = 27;
    private static final int MAX_SCALE = 9;
    public static final int DEFAULT_PRECISION = 9;

    public static final int DEFAULT_SCALE = 0;
    private static final int MAX_VARCHAR_LENGTH = 65535;
    private static final int MAX_STRING_LENGTH = 1048576;
    private static final int MAX_DATETIME_SCALE = 6;

    public static final SelectDBTypeConverter INSTANCE = new SelectDBTypeConverter();

    @Override
    public String identifier() {
        return IDENTIFIER;
    }

    @Override
    public Column convert(BasicTypeDefine typeDefine) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BasicTypeDefine reconvert(Column column) {
        BasicTypeDefine.BasicTypeDefineBuilder builder =
                BasicTypeDefine.<MysqlType>builder()
                        .name(column.getName())
                        .nullable(column.isNullable())
                        .comment(column.getComment())
                        .defaultValue(column.getDefaultValue());

        switch (column.getDataType().getSqlType()) {
            case BOOLEAN:
                builder.columnType(SELECTDB_BOOLEAN);
                builder.dataType(SELECTDB_BOOLEAN);
                break;
            case TINYINT:
                builder.columnType(SELECTDB_TINYINT);
                builder.dataType(SELECTDB_TINYINT);
                break;
            case SMALLINT:
                builder.columnType(SELECTDB_SMALLINT);
                builder.dataType(SELECTDB_SMALLINT);
                break;
            case INT:
                builder.columnType(SELECTDB_INT);
                builder.dataType(SELECTDB_INT);
                break;
            case BIGINT:
                builder.columnType(SELECTDB_BIGINT);
                builder.dataType(SELECTDB_BIGINT);
                break;
            case FLOAT:
                builder.columnType(SELECTDB_FLOAT);
                builder.dataType(SELECTDB_FLOAT);
                break;
            case DOUBLE:
                builder.columnType(SELECTDB_DOUBLE);
                builder.dataType(SELECTDB_DOUBLE);
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

                builder.columnType(
                        String.format("%s(%s,%s)", SELECTDB_DECIMALV3, precision, scale));
                builder.dataType(SELECTDB_DECIMALV3);
                builder.precision(precision);
                builder.scale(scale);
                break;
            case BYTES:
                builder.columnType(SELECTDB_STRING);
                builder.dataType(SELECTDB_STRING);
                break;
            case STRING:
                if (column.getColumnLength() == null || column.getColumnLength() <= 0) {
                    builder.columnType(SELECTDB_STRING);
                    builder.dataType(SELECTDB_STRING);
                } else if (column.getColumnLength() < MAX_VARCHAR_LENGTH) {
                    builder.columnType(
                            String.format("%s(%s)", SELECTDB_VARCHAR, column.getColumnLength()));
                    builder.dataType(SELECTDB_VARCHAR);
                } else {
                    builder.columnType(SELECTDB_STRING);
                    builder.dataType(SELECTDB_STRING);
                }
                break;
            case DATE:
                builder.columnType(SELECTDB_DATE);
                builder.dataType(SELECTDB_DATE);
                break;
            case NULL:
            case TIME:
                builder.columnType(String.format("%s(%s)", SELECTDB_VARCHAR, 8));
                builder.dataType(SELECTDB_VARCHAR);
                break;
            case TIMESTAMP:
                builder.dataType(SELECTDB_DATETIMEV2);
                if (column.getScale() != null && column.getScale() > 0) {
                    int timestampScale = column.getScale();
                    if (timestampScale > MAX_DATETIME_SCALE) {
                        timestampScale = MAX_DATETIME_SCALE;
                        log.warn(
                                "The timestamp column {} type timestamp({}) is out of range, "
                                        + "which exceeds the maximum scale of {}, "
                                        + "it will be converted to timestamp({})",
                                column.getName(),
                                column.getScale(),
                                MAX_DATETIME_SCALE,
                                timestampScale);
                    }
                    builder.columnType(
                            String.format("%s(%s)", SELECTDB_DATETIMEV2, timestampScale));
                    builder.scale(timestampScale);
                } else {
                    builder.columnType(SELECTDB_DATETIMEV2);
                }
                break;
            case ARRAY:
                ArrayType<?, ?> arrayType = (ArrayType<?, ?>) column.getDataType();
                BasicTypeDefine elementTypeDefine =
                        reconvert(
                                PhysicalColumn.of(
                                        column.getName(),
                                        arrayType.getElementType(),
                                        (Long) null,
                                        false,
                                        null,
                                        null));
                builder.dataType(SELECTDB_ARRAY);
                builder.columnType(
                        String.format("%s<%s>", SELECTDB_ARRAY, elementTypeDefine.getColumnType()));
                break;
            case MAP:
            case ROW:
                builder.columnType(SELECTDB_JSON);
                builder.dataType(SELECTDB_JSON);
                break;
            default:
                throw CommonError.convertToConnectorTypeError(
                        IDENTIFIER, column.getDataType().getSqlType().name(), column.getName());
        }

        return builder.build();
    }
}

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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.sqlite;

import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.api.table.converter.TypeConverter;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.DecimalType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.PrimitiveByteArrayType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;

import com.google.auto.service.AutoService;

import java.util.Locale;

@AutoService(TypeConverter.class)
public class SqliteTypeConverter implements TypeConverter<BasicTypeDefine<?>> {

    @Override
    public String identifier() {
        return DatabaseIdentifier.SQLITE;
    }

    @Override
    public Column convert(BasicTypeDefine<?> typeDefine) {
        PhysicalColumn.PhysicalColumnBuilder builder =
                PhysicalColumn.builder()
                        .name(typeDefine.getName())
                        .sourceType(typeDefine.getColumnType())
                        .nullable(typeDefine.isNullable())
                        .defaultValue(typeDefine.getDefaultValue())
                        .comment(typeDefine.getComment());

        String rawType =
                (typeDefine.getColumnType() == null || typeDefine.getColumnType().isEmpty())
                        ? typeDefine.getDataType()
                        : typeDefine.getColumnType();
        String sqliteType =
                rawType == null ? "" : rawType.toUpperCase(Locale.ROOT).trim().replace("\"", "");

        switch (sqliteType) {
            case "INTEGER":
            case "INT":
            case "TINYINT":
            case "SMALLINT":
            case "MEDIUMINT":
            case "BIGINT":
            case "UNSIGNED BIG INT":
            case "INT2":
            case "INT8":
                builder.dataType(BasicType.LONG_TYPE);
                break;
            case "REAL":
            case "DOUBLE":
            case "DOUBLE PRECISION":
            case "FLOAT":
                builder.dataType(BasicType.DOUBLE_TYPE);
                break;
            case "NUMERIC":
            case "DECIMAL":
                int precision =
                        typeDefine.getPrecision() == null
                                ? 10
                                : typeDefine.getPrecision().intValue();
                int scale = typeDefine.getScale() == null ? 0 : typeDefine.getScale();
                builder.dataType(new DecimalType(precision, scale));
                builder.columnLength((long) precision);
                builder.scale(scale);
                break;
            case "BOOLEAN":
                builder.dataType(BasicType.BOOLEAN_TYPE);
                break;
            case "DATE":
                builder.dataType(LocalTimeType.LOCAL_DATE_TYPE);
                break;
            case "DATETIME":
                builder.dataType(LocalTimeType.LOCAL_DATE_TIME_TYPE);
                break;
            case "BLOB":
                builder.dataType(PrimitiveByteArrayType.INSTANCE);
                break;
            case "TEXT":
            case "CLOB":
            case "VARCHAR":
            case "CHARACTER":
            case "VARYING CHARACTER":
            case "NCHAR":
            case "NATIVE CHARACTER":
            case "NVARCHAR":
            default:
                builder.dataType(BasicType.STRING_TYPE);
                if (typeDefine.getLength() != null) {
                    builder.columnLength(typeDefine.getLength());
                }
                break;
        }
        return builder.build();
    }

    @Override
    public BasicTypeDefine<?> reconvert(Column column) {
        BasicTypeDefine.BasicTypeDefineBuilder<?> builder =
                BasicTypeDefine.builder()
                        .name(column.getName())
                        .nullable(column.isNullable())
                        .comment(column.getComment())
                        .defaultValue(column.getDefaultValue());
        SeaTunnelDataType<?> dataType = column.getDataType();
        switch (dataType.getSqlType()) {
            case TINYINT:
            case SMALLINT:
            case INT:
            case BIGINT:
                builder.columnType("INTEGER");
                builder.dataType("INTEGER");
                break;
            case FLOAT:
            case DOUBLE:
                builder.columnType("REAL");
                builder.dataType("REAL");
                break;
            case DECIMAL:
                DecimalType decimalType = (DecimalType) dataType;
                builder.columnType("NUMERIC");
                builder.dataType("NUMERIC");
                builder.precision((long) decimalType.getPrecision());
                builder.scale(decimalType.getScale());
                builder.length((long) decimalType.getPrecision());
                break;
            case BOOLEAN:
                builder.columnType("BOOLEAN");
                builder.dataType("BOOLEAN");
                break;
            case DATE:
                builder.columnType("DATE");
                builder.dataType("DATE");
                break;
            case TIMESTAMP:
                builder.columnType("DATETIME");
                builder.dataType("DATETIME");
                break;
            case BYTES:
                builder.columnType("BLOB");
                builder.dataType("BLOB");
                break;
            case STRING:
            default:
                builder.columnType("TEXT");
                builder.dataType("TEXT");
                builder.length(column.getColumnLength());
                break;
        }
        return builder.build();
    }
}

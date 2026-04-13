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

package org.apache.seatunnel.connectors.seatunnel.hudi.catalog;

import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.converter.BasicTypeConverter;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.api.table.converter.TypeConverter;
import org.apache.seatunnel.api.table.type.ArrayType;
import org.apache.seatunnel.api.table.type.DecimalType;
import org.apache.seatunnel.api.table.type.MapType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.common.exception.CommonError;

import com.google.auto.service.AutoService;

@AutoService(TypeConverter.class)
public class HudiTypeConverter implements BasicTypeConverter<BasicTypeDefine<String>> {

    private static final String IDENTIFIER = "HUDI";

    @Override
    public String identifier() {
        return IDENTIFIER;
    }

    @Override
    public Column convert(BasicTypeDefine<String> typeDefine) {
        throw new UnsupportedOperationException("Hudi converter only supports sink schema infer.");
    }

    @Override
    public BasicTypeDefine<String> reconvert(Column column) {
        return BasicTypeDefine.<String>builder()
                .name(column.getName())
                .nullable(column.isNullable())
                .comment(column.getComment())
                .defaultValue(column.getDefaultValue())
                .columnType(toHudiType(column.getDataType(), column.getName()))
                .dataType(column.getDataType().getSqlType().name())
                .scale(column.getScale())
                .length(column.getColumnLength())
                .build();
    }

    private String toHudiType(SeaTunnelDataType<?> dataType, String fieldName) {
        switch (dataType.getSqlType()) {
            case BOOLEAN:
                return "boolean";
            case TINYINT:
            case SMALLINT:
            case INT:
                return "int";
            case BIGINT:
                return "long";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            case STRING:
                return "string";
            case BYTES:
                return "bytes";
            case DATE:
                return "date";
            case TIME:
                return "time-millis";
            case TIMESTAMP:
                return "timestamp-millis";
            case DECIMAL:
                DecimalType decimalType = (DecimalType) dataType;
                return String.format(
                        "decimal(%s,%s)", decimalType.getPrecision(), decimalType.getScale());
            case ARRAY:
                ArrayType<?, ?> arrayType = (ArrayType<?, ?>) dataType;
                return String.format(
                        "array<%s>",
                        toHudiType(arrayType.getElementType(), fieldName + ".element"));
            case MAP:
                MapType<?, ?> mapType = (MapType<?, ?>) dataType;
                return String.format(
                        "map<string,%s>", toHudiType(mapType.getValueType(), fieldName + ".value"));
            case ROW:
                return "record";
            default:
                throw CommonError.convertToConnectorTypeError(
                        IDENTIFIER, dataType.getSqlType().name(), fieldName);
        }
    }
}

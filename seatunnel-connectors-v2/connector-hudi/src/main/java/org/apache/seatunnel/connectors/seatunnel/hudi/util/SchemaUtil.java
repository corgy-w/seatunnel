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

package org.apache.seatunnel.connectors.seatunnel.hudi.util;

import org.apache.seatunnel.api.table.type.ArrayType;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.DecimalType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.MapType;
import org.apache.seatunnel.api.table.type.PrimitiveByteArrayType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.exception.CommonError;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.List;

public final class SchemaUtil {

    private SchemaUtil() {}

    public static SeaTunnelDataType<?> convertSeaTunnelType(String field, Schema schema) {
        switch (schema.getType()) {
            case RECORD:
                return convertStructType(schema);
            case ENUM:
            case STRING:
            case NULL:
                return BasicType.STRING_TYPE;
            case ARRAY:
                return convertListType(field, schema);
            case MAP:
                return convertMapType(field, schema);
            case BYTES:
            case FIXED:
                if (schema.getLogicalType() instanceof LogicalTypes.Decimal) {
                    LogicalTypes.Decimal decimalType =
                            (LogicalTypes.Decimal) schema.getLogicalType();
                    return new DecimalType(decimalType.getPrecision(), decimalType.getScale());
                }
                return PrimitiveByteArrayType.INSTANCE;
            case INT:
                if (schema.getLogicalType() == LogicalTypes.date()) {
                    return LocalTimeType.LOCAL_DATE_TYPE;
                }
                if (schema.getLogicalType() == LogicalTypes.timeMillis()) {
                    return LocalTimeType.LOCAL_TIME_TYPE;
                }
                return BasicType.INT_TYPE;
            case LONG:
                if (schema.getLogicalType() == LogicalTypes.timestampMillis()
                        || schema.getLogicalType() == LogicalTypes.timestampMicros()
                        || schema.getLogicalType() == LogicalTypes.timeMicros()) {
                    return LocalTimeType.LOCAL_DATE_TIME_TYPE;
                }
                return BasicType.LONG_TYPE;
            case FLOAT:
                return BasicType.FLOAT_TYPE;
            case DOUBLE:
                return BasicType.DOUBLE_TYPE;
            case BOOLEAN:
                return BasicType.BOOLEAN_TYPE;
            case UNION:
                return convertSeaTunnelType(field, unwrapNullableUnion(field, schema));
            default:
                throw CommonError.convertToSeaTunnelTypeError(
                        "Hudi", schema.getType().name(), field);
        }
    }

    private static Schema unwrapNullableUnion(String field, Schema schema) {
        if (schema.getTypes().size() == 2
                && schema.getTypes().get(0).getType() == Schema.Type.NULL) {
            return schema.getTypes().get(1);
        }
        if (schema.getTypes().size() == 2
                && schema.getTypes().get(1).getType() == Schema.Type.NULL) {
            return schema.getTypes().get(0);
        }
        if (schema.getTypes().size() == 1) {
            return schema.getTypes().get(0);
        }
        throw CommonError.convertToSeaTunnelTypeError("Hudi", schema.getType().name(), field);
    }

    private static MapType<?, ?> convertMapType(String field, Schema schema) {
        return new MapType<>(
                BasicType.STRING_TYPE, convertSeaTunnelType(field, schema.getValueType()));
    }

    private static SeaTunnelRowType convertStructType(Schema schema) {
        List<Schema.Field> fields = schema.getFields();
        List<String> fieldNames = new ArrayList<>(fields.size());
        List<SeaTunnelDataType<?>> fieldTypes = new ArrayList<>(fields.size());
        for (Schema.Field field : fields) {
            fieldNames.add(field.name());
            fieldTypes.add(convertSeaTunnelType(field.name(), field.schema()));
        }
        return new SeaTunnelRowType(
                fieldNames.toArray(new String[0]), fieldTypes.toArray(new SeaTunnelDataType[0]));
    }

    private static ArrayType<?, ?> convertListType(String field, Schema schema) {
        SeaTunnelDataType<?> elementType = convertSeaTunnelType(field, schema.getElementType());
        return new ArrayType<>(Object[].class, elementType);
    }
}

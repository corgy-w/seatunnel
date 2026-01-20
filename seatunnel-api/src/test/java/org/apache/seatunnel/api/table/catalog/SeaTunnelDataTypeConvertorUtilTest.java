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

package org.apache.seatunnel.api.table.catalog;

import org.apache.seatunnel.api.table.type.ArrayType;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.DecimalType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.MapType;
import org.apache.seatunnel.api.table.type.PrimitiveByteArrayType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.common.exception.SeaTunnelRuntimeException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SeaTunnelDataTypeConvertorUtilTest {

    @Test
    void testParseTypes() {
        // Basic Types
        Assertions.assertEquals(
                BasicType.STRING_TYPE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "STRING"));
        Assertions.assertEquals(
                BasicType.BOOLEAN_TYPE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "BOOLEAN"));
        Assertions.assertEquals(
                BasicType.BYTE_TYPE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "TINYINT"));
        Assertions.assertEquals(
                BasicType.SHORT_TYPE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "SMALLINT"));
        Assertions.assertEquals(
                BasicType.INT_TYPE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "INT"));
        Assertions.assertEquals(
                BasicType.LONG_TYPE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "BIGINT"));
        Assertions.assertEquals(
                BasicType.FLOAT_TYPE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "FLOAT"));
        Assertions.assertEquals(
                BasicType.DOUBLE_TYPE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "DOUBLE"));
        Assertions.assertEquals(
                BasicType.VOID_TYPE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "NULL"));
        Assertions.assertEquals(
                PrimitiveByteArrayType.INSTANCE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "BYTES"));
        Assertions.assertEquals(
                LocalTimeType.LOCAL_DATE_TYPE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "DATE"));
        Assertions.assertEquals(
                LocalTimeType.LOCAL_TIME_TYPE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "TIME"));
        Assertions.assertEquals(
                LocalTimeType.LOCAL_DATE_TIME_TYPE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "TIMESTAMP"));

        // Decimal
        Assertions.assertEquals(
                new DecimalType(10, 2),
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "DECIMAL(10, 2)"));

        // Array
        Assertions.assertEquals(
                ArrayType.INT_ARRAY_TYPE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "ARRAY<INT>"));
        Assertions.assertEquals(
                ArrayType.STRING_ARRAY_TYPE,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("f", "ARRAY<STRING>"));

        // Map
        Assertions.assertEquals(
                new MapType<>(BasicType.STRING_TYPE, BasicType.INT_TYPE),
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                        "f", "MAP<STRING, INT>"));
        Assertions.assertEquals(
                new MapType<>(
                        BasicType.STRING_TYPE,
                        new MapType<>(BasicType.STRING_TYPE, BasicType.INT_TYPE)),
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                        "f", "MAP<STRING, MAP<STRING, INT>>"));

        SeaTunnelRowType expectedRowTypeLegacy =
                new SeaTunnelRowType(
                        new String[] {"age", "name"},
                        new SeaTunnelDataType[] {BasicType.INT_TYPE, BasicType.STRING_TYPE});

        SeaTunnelDataType<?> rowTypeLegacy =
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                        "f", "{name = STRING, age = INT}");
        Assertions.assertTrue(rowTypeLegacy instanceof SeaTunnelRowType);

        SeaTunnelRowType expectedRowTypeNew =
                new SeaTunnelRowType(
                        new String[] {"name", "age"},
                        new SeaTunnelDataType[] {BasicType.STRING_TYPE, BasicType.INT_TYPE});
        Assertions.assertEquals(
                expectedRowTypeNew,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                        "f", "ROW<name STRING, age INT>"));

        SeaTunnelRowType expectedNestedRowType =
                new SeaTunnelRowType(
                        new String[] {"f1", "f2"},
                        new SeaTunnelDataType[] {
                            BasicType.STRING_TYPE,
                            new SeaTunnelRowType(
                                    new String[] {"sub_f1"},
                                    new SeaTunnelDataType[] {BasicType.INT_TYPE})
                        });
        Assertions.assertEquals(
                expectedNestedRowType,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                        "f", "ROW<f1 STRING, f2 ROW<sub_f1 INT>>"));

        SeaTunnelRowType expectedComplexRowType =
                new SeaTunnelRowType(
                        new String[] {"list", "map"},
                        new SeaTunnelDataType[] {
                            ArrayType.INT_ARRAY_TYPE,
                            new MapType<>(BasicType.STRING_TYPE, BasicType.LONG_TYPE)
                        });
        Assertions.assertEquals(
                expectedComplexRowType,
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                        "f", "ROW<list ARRAY<INT>, map MAP<STRING, BIGINT>>"));
    }

    @Test
    void testParseWithUnsupportedType() {
        SeaTunnelRuntimeException exception =
                Assertions.assertThrows(
                        SeaTunnelRuntimeException.class,
                        () ->
                                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                        "test", "MULTIPLE_ROW"));
        Assertions.assertEquals(
                "ErrorCode:[COMMON-07], ErrorDescription:['SeaTunnel' unsupported data type 'MULTIPLE_ROW' of 'test']",
                exception.getMessage());

        SeaTunnelRuntimeException exception2 =
                Assertions.assertThrows(
                        SeaTunnelRuntimeException.class,
                        () ->
                                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                        "test", "map<string, MULTIPLE_ROW>"));
        Assertions.assertEquals(
                "ErrorCode:[COMMON-07], ErrorDescription:['SeaTunnel' unsupported data type 'MULTIPLE_ROW' of 'test']",
                exception2.getMessage());

        SeaTunnelRuntimeException exception3 =
                Assertions.assertThrows(
                        SeaTunnelRuntimeException.class,
                        () ->
                                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                        "test", "array<MULTIPLE_ROW>"));
        Assertions.assertEquals(
                "ErrorCode:[COMMON-07], ErrorDescription:['SeaTunnel' unsupported data type 'MULTIPLE_ROW' of 'test']",
                exception3.getMessage());

        SeaTunnelRuntimeException exception4 =
                Assertions.assertThrows(
                        SeaTunnelRuntimeException.class,
                        () ->
                                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                        "test", "uuid"));
        Assertions.assertEquals(
                "ErrorCode:[COMMON-07], ErrorDescription:['SeaTunnel' unsupported data type 'uuid' of 'test']",
                exception4.getMessage());

        IllegalArgumentException exception5 =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                        "test", "{uuid}"));
        String expectedMsg5 =
                String.format("HOCON Config parse from %s failed.", "{conf = {uuid}}");
        Assertions.assertEquals(expectedMsg5, exception5.getMessage());

        String invalidTypeDeclaration = "[e]";
        IllegalArgumentException exception6 =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                        "test",
                                        String.format("{c_0 = %s}", invalidTypeDeclaration)));
        String expectedMsg6 =
                String.format(
                        "Unsupported parse SeaTunnel Type from '%s'.", invalidTypeDeclaration);
        Assertions.assertEquals(expectedMsg6, exception6.getMessage());
    }

    @Test
    public void testCompatibleTypeDeclare() {
        SeaTunnelDataType<?> longType =
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("c_long", "long");
        Assertions.assertEquals(BasicType.LONG_TYPE, longType);

        SeaTunnelDataType<?> shortType =
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("c_short", "short");
        Assertions.assertEquals(BasicType.SHORT_TYPE, shortType);

        SeaTunnelDataType<?> byteType =
                SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType("c_byte", "byte");
        Assertions.assertEquals(BasicType.BYTE_TYPE, byteType);

        ArrayType<?, ?> longArrayType =
                (ArrayType<?, ?>)
                        SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                "c_long_array", "array<long>");
        Assertions.assertEquals(ArrayType.LONG_ARRAY_TYPE, longArrayType);

        ArrayType<?, ?> shortArrayType =
                (ArrayType<?, ?>)
                        SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                "c_short_array", "array<short>");
        Assertions.assertEquals(ArrayType.SHORT_ARRAY_TYPE, shortArrayType);

        ArrayType<?, ?> byteArrayType =
                (ArrayType<?, ?>)
                        SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                "c_byte_array", "array<byte>");
        Assertions.assertEquals(ArrayType.BYTE_ARRAY_TYPE, byteArrayType);

        MapType<?, ?> longMapType =
                (MapType<?, ?>)
                        SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                "c_long_map", "map<long, long>");
        Assertions.assertEquals(BasicType.LONG_TYPE, longMapType.getKeyType());
        Assertions.assertEquals(BasicType.LONG_TYPE, longMapType.getValueType());

        MapType<?, ?> shortMapType =
                (MapType<?, ?>)
                        SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                "c_short_map", "map<short, short>");
        Assertions.assertEquals(BasicType.SHORT_TYPE, shortMapType.getKeyType());
        Assertions.assertEquals(BasicType.SHORT_TYPE, shortMapType.getValueType());

        MapType<?, ?> byteMapType =
                (MapType<?, ?>)
                        SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                "c_byte_map", "map<byte, byte>");
        Assertions.assertEquals(BasicType.BYTE_TYPE, byteMapType.getKeyType());
        Assertions.assertEquals(BasicType.BYTE_TYPE, byteMapType.getValueType());

        SeaTunnelRowType longRow =
                (SeaTunnelRowType)
                        SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                "c_long_row", "{c = long}");
        Assertions.assertEquals(BasicType.LONG_TYPE, longRow.getFieldType(0));

        SeaTunnelRowType shortRow =
                (SeaTunnelRowType)
                        SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                "c_short_row", "{c = short}");
        Assertions.assertEquals(BasicType.SHORT_TYPE, shortRow.getFieldType(0));

        SeaTunnelRowType byteRow =
                (SeaTunnelRowType)
                        SeaTunnelDataTypeConvertorUtil.deserializeSeaTunnelDataType(
                                "c_byte_row", "{c = byte}");
        Assertions.assertEquals(BasicType.BYTE_TYPE, byteRow.getFieldType(0));
    }
}

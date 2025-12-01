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

package org.apache.seatunnel.connectors.seatunnel.clickhouse.util;

import org.apache.seatunnel.api.table.type.ArrayType;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.clickhouse.client.ClickHouseColumn;
import com.clickhouse.client.ClickHouseDataType;

public class TypeConvertUtilTest {

    @Test
    public void testGetColumnLengthForStringTypeIsNull() {
        ClickHouseColumn column = ClickHouseColumn.of("c1", ClickHouseDataType.String, false, 1, 0);

        SeaTunnelDataType<?> dataType = TypeConvertUtil.convert(column);
        Assertions.assertEquals(BasicType.STRING_TYPE, dataType);

        Long length = TypeConvertUtil.getColumnLength(column, dataType);
        Assertions.assertNull(length, "String column length should be null");
    }

    @Test
    public void testGetColumnLengthForNonStringTypeUsesEstimatedLength() {
        ClickHouseColumn column = ClickHouseColumn.of("c2", ClickHouseDataType.Int32, false, 10, 0);

        SeaTunnelDataType<?> dataType = TypeConvertUtil.convert(column);
        Assertions.assertEquals(BasicType.INT_TYPE, dataType);

        Long length = TypeConvertUtil.getColumnLength(column, dataType);
        Assertions.assertEquals(
                column.getEstimatedLength(), length.longValue(), "Should use estimatedLength");
    }

    @Test
    public void testGetColumnLengthForStringArrayTypeIsNull() {
        ClickHouseColumn element =
                ClickHouseColumn.of("elem", ClickHouseDataType.String, false, 1, 0);
        ClickHouseColumn arrayColumn =
                ClickHouseColumn.of("arr", ClickHouseDataType.Array, false, element);

        SeaTunnelDataType<?> dataType = TypeConvertUtil.convert(arrayColumn);
        Assertions.assertTrue(dataType instanceof ArrayType);

        Long length = TypeConvertUtil.getColumnLength(arrayColumn, dataType);
        Assertions.assertEquals(
                arrayColumn.getEstimatedLength(),
                length.longValue(),
                "Array column length should use estimatedLength");
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.DecimalType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SqliteTypeConverterTest {

    private final SqliteTypeConverter converter = new SqliteTypeConverter();

    @Test
    public void testConvertInteger() {
        BasicTypeDefine<?> define =
                BasicTypeDefine.builder()
                        .name("id")
                        .columnType("INTEGER")
                        .dataType("INTEGER")
                        .nullable(false)
                        .build();
        Column column = converter.convert(define);
        Assertions.assertEquals("id", column.getName());
        Assertions.assertEquals(BasicType.LONG_TYPE, column.getDataType());
        Assertions.assertFalse(column.isNullable());
    }

    @Test
    public void testConvertDecimal() {
        BasicTypeDefine<?> define =
                BasicTypeDefine.builder()
                        .name("price")
                        .columnType("NUMERIC")
                        .dataType("NUMERIC")
                        .precision(10L)
                        .scale(2)
                        .build();
        Column column = converter.convert(define);
        Assertions.assertEquals(new DecimalType(10, 2), column.getDataType());
    }

    @Test
    public void testReconvertString() {
        PhysicalColumn column =
                PhysicalColumn.of(
                        "name",
                        BasicType.STRING_TYPE,
                        255L,
                        null,
                        true,
                        "",
                        "comment",
                        "TEXT",
                        null);

        BasicTypeDefine<?> define = converter.reconvert(column);
        Assertions.assertEquals("TEXT", define.getColumnType());
        Assertions.assertEquals("TEXT", define.getDataType());
        Assertions.assertEquals(255L, define.getLength());
    }
}

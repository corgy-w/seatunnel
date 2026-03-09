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

package org.apache.seatunnel.connectors.dolphindb.sink.writter;

import org.apache.seatunnel.api.table.type.BasicType;

import org.junit.jupiter.api.Test;

import com.xxdb.data.BasicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DolphinDBUpsertWriterTest {

    @Test
    void coerceBooleanFromStringAndNumber() {
        assertEquals(
                true,
                DolphinDBUpsertWriter.coerceFieldValue(
                        BasicType.BOOLEAN_TYPE, "boolean_col", "true"));
        assertEquals(
                false,
                DolphinDBUpsertWriter.coerceFieldValue(
                        BasicType.BOOLEAN_TYPE, "boolean_col", "false"));
        assertEquals(
                true,
                DolphinDBUpsertWriter.coerceFieldValue(BasicType.BOOLEAN_TYPE, "boolean_col", "1"));
        assertEquals(
                false,
                DolphinDBUpsertWriter.coerceFieldValue(BasicType.BOOLEAN_TYPE, "boolean_col", "0"));
        assertEquals(
                true,
                DolphinDBUpsertWriter.coerceFieldValue(BasicType.BOOLEAN_TYPE, "boolean_col", 1));
        assertEquals(
                false,
                DolphinDBUpsertWriter.coerceFieldValue(BasicType.BOOLEAN_TYPE, "boolean_col", 0));
        assertNull(
                DolphinDBUpsertWriter.coerceFieldValue(
                        BasicType.BOOLEAN_TYPE, "boolean_col", "null"));
        assertNull(
                DolphinDBUpsertWriter.coerceFieldValue(BasicType.BOOLEAN_TYPE, "boolean_col", ""));
        assertEquals(
                true,
                DolphinDBUpsertWriter.coerceFieldValue(
                        BasicType.BOOLEAN_TYPE, "boolean_col", "on"));
        assertEquals(
                false,
                DolphinDBUpsertWriter.coerceFieldValue(
                        BasicType.BOOLEAN_TYPE, "boolean_col", "off"));
        assertEquals(
                true,
                DolphinDBUpsertWriter.coerceFieldValue(
                        BasicType.BOOLEAN_TYPE, "boolean_col", new byte[] {1}));
        assertEquals(
                false,
                DolphinDBUpsertWriter.coerceFieldValue(
                        BasicType.BOOLEAN_TYPE, "boolean_col", new byte[] {0}));
        assertEquals(
                true,
                DolphinDBUpsertWriter.coerceFieldValue(
                        BasicType.BOOLEAN_TYPE, "boolean_col", "AQ=="));
        assertEquals(
                false,
                DolphinDBUpsertWriter.coerceFieldValue(
                        BasicType.BOOLEAN_TYPE, "boolean_col", "AA=="));
    }

    @Test
    void coerceBooleanByDolphinTypeEvenIfSourceTypeIsString() {
        assertTrue(
                DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOL", "1")
                        instanceof BasicBoolean);
        assertTrue(
                DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOL", "0")
                        instanceof BasicBoolean);
        assertTrue(
                DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOL", "true")
                        instanceof BasicBoolean);
        assertTrue(
                DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOL", "false")
                        instanceof BasicBoolean);
        assertTrue(
                DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOL", "AQ==")
                        instanceof BasicBoolean);
        assertTrue(
                DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOL", "AA==")
                        instanceof BasicBoolean);
        assertTrue(
                DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOLEAN", 1)
                        instanceof BasicBoolean);
        assertTrue(
                DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOLEAN", 0)
                        instanceof BasicBoolean);

        assertEquals(
                true,
                ((BasicBoolean) DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOL", "1"))
                        .getBoolean());
        assertEquals(
                false,
                ((BasicBoolean) DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOL", "0"))
                        .getBoolean());
        assertEquals(
                true,
                ((BasicBoolean) DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOL", "true"))
                        .getBoolean());
        assertEquals(
                false,
                ((BasicBoolean)
                                DolphinDBUpsertWriter.coerceFieldValueByDolphinType(
                                        "BOOL", "false"))
                        .getBoolean());
        assertEquals(
                true,
                ((BasicBoolean) DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOL", "AQ=="))
                        .getBoolean());
        assertEquals(
                false,
                ((BasicBoolean) DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOL", "AA=="))
                        .getBoolean());
        assertEquals(
                true,
                ((BasicBoolean) DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOLEAN", 1))
                        .getBoolean());
        assertEquals(
                false,
                ((BasicBoolean) DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOLEAN", 0))
                        .getBoolean());

        Object nullBool = DolphinDBUpsertWriter.coerceFieldValueByDolphinType("BOOL", null);
        assertTrue(nullBool instanceof BasicBoolean);
        assertTrue(((BasicBoolean) nullBool).isNull());
    }
}

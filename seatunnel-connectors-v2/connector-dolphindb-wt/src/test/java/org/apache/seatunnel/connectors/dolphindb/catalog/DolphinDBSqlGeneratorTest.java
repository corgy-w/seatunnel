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

package org.apache.seatunnel.connectors.dolphindb.catalog;

import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DolphinDBSqlGeneratorTest {

    @Test
    void generateDeleteRowSqlUsesOnlyKeyIndexes() {
        SeaTunnelRowType rowType =
                new SeaTunnelRowType(
                        new String[] {"id", "json_col", "f"},
                        new SeaTunnelDataType[] {
                            BasicType.INT_TYPE, BasicType.STRING_TYPE, BasicType.FLOAT_TYPE
                        });

        assertEquals(
                "delete from t where id = ?",
                DolphinDBSqlGenerator.generateDeleteRowSql("t", rowType, new int[] {0}));

        assertEquals(
                "delete from t where f = float(?)",
                DolphinDBSqlGenerator.generateDeleteRowSql("t", rowType, new int[] {2}));

        assertEquals(
                "delete from t where id = ? , f = float(?)",
                DolphinDBSqlGenerator.generateDeleteRowSql("t", rowType, new int[] {0, 2}));
    }

    @Test
    void generateDeleteRowSqlWithoutKeyFallsBackToAllFields() {
        SeaTunnelRowType rowType =
                new SeaTunnelRowType(
                        new String[] {"id", "name"},
                        new SeaTunnelDataType[] {BasicType.INT_TYPE, BasicType.STRING_TYPE});

        assertEquals(
                "delete from t where id = ? , name = ?",
                DolphinDBSqlGenerator.generateDeleteRowSql("db", "t", rowType));
    }
}

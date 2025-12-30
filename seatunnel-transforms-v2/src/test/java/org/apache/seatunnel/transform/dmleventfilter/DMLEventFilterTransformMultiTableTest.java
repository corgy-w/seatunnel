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

package org.apache.seatunnel.transform.dmleventfilter;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DMLEventFilterTransformMultiTableTest {

    private static CatalogTable TABLE_A;
    private static CatalogTable TABLE_B;

    @BeforeAll
    static void setUp() {
        TableSchema schema =
                TableSchema.builder()
                        .column(PhysicalColumn.of("id", BasicType.INT_TYPE, 0L, false, null, null))
                        .column(
                                PhysicalColumn.of(
                                        "name", BasicType.STRING_TYPE, 50L, true, null, null))
                        .build();

        TABLE_A =
                CatalogTable.of(
                        TableIdentifier.of("catalog", "db", "table_a"),
                        schema,
                        new HashMap<>(),
                        new ArrayList<>(),
                        "table a");

        TABLE_B =
                CatalogTable.of(
                        TableIdentifier.of("catalog", "db", "table_b"),
                        schema,
                        new HashMap<>(),
                        new ArrayList<>(),
                        "table b");
    }

    private SeaTunnelRow rowOf(CatalogTable table, RowKind kind, Object... fields) {
        SeaTunnelRow row = new SeaTunnelRow(fields);
        row.setRowKind(kind);
        row.setTableId(table.getTableId().toTablePath().toString());
        return row;
    }

    @Test
    void testLegacyModeMultiTableRouting() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put(
                DMLEventFilterTransformConfig.EXCLUDE_KINDS.key(), Arrays.asList(RowKind.DELETE));

        ReadonlyConfig cfg = ReadonlyConfig.fromMap(configMap);
        List<CatalogTable> inputTables = Arrays.asList(TABLE_A, TABLE_B);

        DMLEventFilterTransform transform = new DMLEventFilterTransform(inputTables, cfg);

        List<CatalogTable> producedTables = transform.getProducedCatalogTables();
        Assertions.assertEquals(2, producedTables.size());
        Assertions.assertEquals(TABLE_A.getTableId(), producedTables.get(0).getTableId());
        Assertions.assertEquals(TABLE_B.getTableId(), producedTables.get(1).getTableId());

        SeaTunnelRow insertA = rowOf(TABLE_A, RowKind.INSERT, 1, "a");
        SeaTunnelRow deleteA = rowOf(TABLE_A, RowKind.DELETE, 1, "a");
        SeaTunnelRow insertB = rowOf(TABLE_B, RowKind.INSERT, 2, "b");
        SeaTunnelRow deleteB = rowOf(TABLE_B, RowKind.DELETE, 2, "b");

        SeaTunnelRow outInsertA = transform.map(insertA);
        SeaTunnelRow outDeleteA = transform.map(deleteA);
        SeaTunnelRow outInsertB = transform.map(insertB);
        SeaTunnelRow outDeleteB = transform.map(deleteB);

        Assertions.assertNotNull(outInsertA);
        Assertions.assertEquals(
                TABLE_A.getTableId().toTablePath().toString(), outInsertA.getTableId());
        Assertions.assertNull(outDeleteA);

        Assertions.assertNotNull(outInsertB);
        Assertions.assertEquals(
                TABLE_B.getTableId().toTablePath().toString(), outInsertB.getTableId());
        Assertions.assertNull(outDeleteB);
    }

    @Test
    void testAppendModeSchemaPerTable() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put(
                DMLEventFilterTransformConfig.PROCESSING_MODE.key(),
                ProcessingMode.APPEND_MODE.name());
        configMap.put(DMLEventFilterTransformConfig.TIMESTAMP_FIELD_NAME.key(), "op_ts");
        configMap.put(DMLEventFilterTransformConfig.DML_MARKER_FIELD_NAME.key(), "op_type");

        ReadonlyConfig cfg = ReadonlyConfig.fromMap(configMap);
        List<CatalogTable> inputTables = Arrays.asList(TABLE_A, TABLE_B);

        DMLEventFilterTransform transform = new DMLEventFilterTransform(inputTables, cfg);

        List<CatalogTable> produced = transform.getProducedCatalogTables();
        Assertions.assertEquals(2, produced.size());
        Assertions.assertEquals(4, produced.get(0).getTableSchema().getColumns().size());
        Assertions.assertEquals(4, produced.get(1).getTableSchema().getColumns().size());

        SeaTunnelRow insertA = rowOf(TABLE_A, RowKind.INSERT, 1, "a");
        SeaTunnelRow insertB = rowOf(TABLE_B, RowKind.INSERT, 2, "b");

        SeaTunnelRow outA = transform.map(insertA);
        SeaTunnelRow outB = transform.map(insertB);

        Assertions.assertEquals(RowKind.INSERT, outA.getRowKind());
        Assertions.assertEquals(RowKind.INSERT, outB.getRowKind());
        Assertions.assertEquals(TABLE_A.getTableId().toTablePath().toString(), outA.getTableId());
        Assertions.assertEquals(TABLE_B.getTableId().toTablePath().toString(), outB.getTableId());
        Assertions.assertEquals(4, outA.getFields().length);
        Assertions.assertEquals(4, outB.getFields().length);
    }
}

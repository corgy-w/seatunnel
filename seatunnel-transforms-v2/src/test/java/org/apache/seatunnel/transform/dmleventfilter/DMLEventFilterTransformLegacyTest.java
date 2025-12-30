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
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DMLEventFilterTransformLegacyTest {

    private static CatalogTable INPUT_TABLE;

    @BeforeAll
    static void setUp() {
        TableSchema schema =
                TableSchema.builder()
                        .column(PhysicalColumn.of("id", BasicType.INT_TYPE, 0L, false, null, null))
                        .column(
                                PhysicalColumn.of(
                                        "name", BasicType.STRING_TYPE, 50L, true, null, null))
                        .primaryKey(PrimaryKey.of("pk_id", Collections.singletonList("id")))
                        .build();
        INPUT_TABLE =
                CatalogTable.of(
                        TableIdentifier.of("catalog", "database", "table"),
                        schema,
                        new HashMap<>(),
                        new ArrayList<>(),
                        "comment");
    }

    private SeaTunnelRow rowOf(RowKind kind, Object... fields) {
        SeaTunnelRow r = new SeaTunnelRow(fields);
        r.setRowKind(kind);
        r.setTableId(INPUT_TABLE.getTableId().toString());
        return r;
    }

    private DMLEventFilterTransform buildTransform(Map<String, Object> map) {
        ReadonlyConfig cfg = ReadonlyConfig.fromMap(map);
        DMLEventFilterTransform t =
                new DMLEventFilterTransform(Collections.singletonList(INPUT_TABLE), cfg);
        // 触发表结构推导（虽然旧模式不需要，但保持一致）
        t.getProducedCatalogTable();
        return t;
    }

    @Test
    void testLegacyExcludeOnly() {
        Map<String, Object> m = new HashMap<>();
        m.put(DMLEventFilterTransformConfig.EXCLUDE_KINDS.key(), Arrays.asList(RowKind.DELETE));
        DMLEventFilterTransform t = buildTransform(m);

        SeaTunnelRow insert = rowOf(RowKind.INSERT, 1, "a");
        SeaTunnelRow delete = rowOf(RowKind.DELETE, 1, "a");

        Assertions.assertNotNull(t.map(insert));
        Assertions.assertNull(t.map(delete));
    }

    @Test
    void testLegacyIncludeOnly() {
        Map<String, Object> m = new HashMap<>();
        m.put(DMLEventFilterTransformConfig.INCLUDE_KINDS.key(), Arrays.asList(RowKind.INSERT));
        DMLEventFilterTransform t = buildTransform(m);

        SeaTunnelRow insert = rowOf(RowKind.INSERT, 1, "a");
        SeaTunnelRow update = rowOf(RowKind.UPDATE_AFTER, 1, "a");

        Assertions.assertNotNull(t.map(insert));
        Assertions.assertNull(t.map(update));
    }

    @Test
    void testLegacyNoneConfiguredPassThrough() {
        Map<String, Object> m = new HashMap<>();
        DMLEventFilterTransform t = buildTransform(m);

        SeaTunnelRow delete = rowOf(RowKind.DELETE, 1, "a");
        Assertions.assertNotNull(t.map(delete));
        Assertions.assertEquals(RowKind.DELETE, t.map(delete).getRowKind());
    }

    @Test
    void testLegacyIncludeAndExcludeTogetherExcludeWins() {
        Map<String, Object> m = new HashMap<>();
        m.put(DMLEventFilterTransformConfig.INCLUDE_KINDS.key(), Arrays.asList(RowKind.INSERT));
        m.put(DMLEventFilterTransformConfig.EXCLUDE_KINDS.key(), Arrays.asList(RowKind.INSERT));
        DMLEventFilterTransform t = buildTransform(m);

        SeaTunnelRow insert = rowOf(RowKind.INSERT, 1, "a");
        Assertions.assertNull(t.map(insert));
    }
}

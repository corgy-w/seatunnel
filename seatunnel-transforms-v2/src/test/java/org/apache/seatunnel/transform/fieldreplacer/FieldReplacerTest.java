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

package org.apache.seatunnel.transform.fieldreplacer;

import org.apache.seatunnel.shade.com.google.common.collect.ImmutableMap;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.ConstraintKey;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;

public class FieldReplacerTest {

    private static final CatalogTable DEFAULT_TABLE =
            CatalogTable.of(
                    TableIdentifier.of("mysql-1", "database-x", null, "table-x"),
                    TableSchema.builder()
                            .column(
                                    PhysicalColumn.of(
                                            "f1",
                                            BasicType.LONG_TYPE,
                                            null,
                                            false,
                                            null,
                                            null,
                                            "int unsigned",
                                            false,
                                            false,
                                            null,
                                            null,
                                            null))
                            .column(
                                    PhysicalColumn.of(
                                            "f2",
                                            BasicType.STRING_TYPE,
                                            10,
                                            false,
                                            null,
                                            null,
                                            "varchar(10)",
                                            false,
                                            false,
                                            null,
                                            null,
                                            null))
                            .column(
                                    PhysicalColumn.of(
                                            "f3",
                                            BasicType.STRING_TYPE,
                                            20,
                                            false,
                                            null,
                                            null,
                                            "varchar(20)",
                                            false,
                                            false,
                                            null,
                                            null,
                                            null))
                            .primaryKey(PrimaryKey.of("pk1", Arrays.asList("f1")))
                            .constraintKey(
                                    ConstraintKey.of(
                                            ConstraintKey.ConstraintType.UNIQUE_KEY,
                                            "uk1",
                                            Arrays.asList(
                                                    ConstraintKey.ConstraintKeyColumn.of(
                                                            "f2", ConstraintKey.ColumnSortType.ASC),
                                                    ConstraintKey.ConstraintKeyColumn.of(
                                                            "f3",
                                                            ConstraintKey.ColumnSortType.ASC))))
                            .build(),
                    Collections.emptyMap(),
                    Collections.singletonList("f2"),
                    null);

    @Test
    public void testReplacerValue() {
        FieldReplacerTransformConfig.FieldReplacer replacerF2 =
                new FieldReplacerTransformConfig.FieldReplacer();
        replacerF2.setTablePath(DEFAULT_TABLE.getTablePath().toString());
        replacerF2.setReplaceField("f2");
        replacerF2.setIsRegex(false);
        replacerF2.setReplacements(new LinkedHashMap<>(ImmutableMap.of("a", "1", "b", "2")));
        FieldReplacerTransformConfig.FieldReplacer replacerF3 =
                new FieldReplacerTransformConfig.FieldReplacer();
        replacerF3.setTablePath(DEFAULT_TABLE.getTablePath().toString());
        replacerF3.setReplaceField("f3");
        replacerF3.setIsRegex(false);
        replacerF3.setReplacements(new LinkedHashMap<>(ImmutableMap.of("b", "2", "a", "1")));
        FieldReplacerTransformConfig config = new FieldReplacerTransformConfig();
        config.setFieldReplacers(Arrays.asList(replacerF2, replacerF3));
        FieldReplacerTransform transform =
                new FieldReplacerTransform(Collections.singletonList(DEFAULT_TABLE), config);

        SeaTunnelRow row = new SeaTunnelRow(new Object[] {1L, "a", "b"});
        row.setTableId(DEFAULT_TABLE.getTablePath().toString());
        SeaTunnelRow result = transform.map(row);
        Assertions.assertEquals("1", result.getField(1));
        Assertions.assertEquals("2", result.getField(2));

        row = new SeaTunnelRow(new Object[] {1L, "ab", "ab"});
        row.setTableId(DEFAULT_TABLE.getTablePath().toString());
        result = transform.map(row);
        Assertions.assertEquals("a2", result.getField(1));
        Assertions.assertEquals("1b", result.getField(2));

        row = new SeaTunnelRow(new Object[] {1L, "ba", "ba"});
        row.setTableId(DEFAULT_TABLE.getTablePath().toString());
        result = transform.map(row);
        Assertions.assertEquals("2a", result.getField(1));
        Assertions.assertEquals("b1", result.getField(2));
    }

    @Test
    public void testReplacerValueWithRegex() {
        FieldReplacerTransformConfig.FieldReplacer replacerF2 =
                new FieldReplacerTransformConfig.FieldReplacer();
        replacerF2.setTablePath(DEFAULT_TABLE.getTablePath().toString());
        replacerF2.setReplaceField("f2");
        replacerF2.setIsRegex(true);
        replacerF2.setReplaceFirst(false);
        replacerF2.setReplacements(new LinkedHashMap<>(ImmutableMap.of("a", "1", "b", "2")));
        FieldReplacerTransformConfig.FieldReplacer replacerF3 =
                new FieldReplacerTransformConfig.FieldReplacer();
        replacerF3.setTablePath(DEFAULT_TABLE.getTablePath().toString());
        replacerF3.setReplaceField("f3");
        replacerF3.setIsRegex(true);
        replacerF3.setReplaceFirst(false);
        replacerF3.setReplacements(new LinkedHashMap<>(ImmutableMap.of("b", "2", "a", "1")));
        FieldReplacerTransformConfig config = new FieldReplacerTransformConfig();
        config.setFieldReplacers(Arrays.asList(replacerF2, replacerF3));
        FieldReplacerTransform transform =
                new FieldReplacerTransform(Collections.singletonList(DEFAULT_TABLE), config);

        SeaTunnelRow row = new SeaTunnelRow(new Object[] {1L, "a", "b"});
        row.setTableId(DEFAULT_TABLE.getTablePath().toString());
        SeaTunnelRow result = transform.map(row);
        Assertions.assertEquals("1", result.getField(1));
        Assertions.assertEquals("2", result.getField(2));

        row = new SeaTunnelRow(new Object[] {1L, "ab", "ab"});
        row.setTableId(DEFAULT_TABLE.getTablePath().toString());
        result = transform.map(row);
        Assertions.assertEquals("a2", result.getField(1));
        Assertions.assertEquals("1b", result.getField(2));

        row = new SeaTunnelRow(new Object[] {1L, "ba", "ba"});
        row.setTableId(DEFAULT_TABLE.getTablePath().toString());
        result = transform.map(row);
        Assertions.assertEquals("2a", result.getField(1));
        Assertions.assertEquals("b1", result.getField(2));
    }
}

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

package org.apache.seatunnel.format.json.customcdc;

import org.apache.seatunnel.api.source.Collector;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.RowKind;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class CustomCdcJsonSourceDeserializationSchemaTest {

    @Test
    void shouldRouteRowsByTableAndOperation() {
        CatalogTable studentTable =
                CatalogTable.of(
                        TableIdentifier.of("", TablePath.of("student", true)),
                        TableSchema.builder()
                                .column(
                                        PhysicalColumn.of(
                                                "studentId",
                                                BasicType.STRING_TYPE,
                                                255L,
                                                true,
                                                null,
                                                null))
                                .column(
                                        PhysicalColumn.of(
                                                "studentName",
                                                BasicType.STRING_TYPE,
                                                255L,
                                                true,
                                                null,
                                                null))
                                .build(),
                        new HashMap<>(),
                        new ArrayList<>(),
                        null);
        CatalogTable teacherTable =
                CatalogTable.of(
                        TableIdentifier.of("", TablePath.of("teacher", true)),
                        TableSchema.builder()
                                .column(
                                        PhysicalColumn.of(
                                                "teacherId",
                                                BasicType.STRING_TYPE,
                                                255L,
                                                true,
                                                null,
                                                null))
                                .column(
                                        PhysicalColumn.of(
                                                "teacherName",
                                                BasicType.STRING_TYPE,
                                                255L,
                                                true,
                                                null,
                                                null))
                                .build(),
                        new HashMap<>(),
                        new ArrayList<>(),
                        null);

        Map<TablePath, Map<String, String>> mappings = new HashMap<>();
        mappings.put(
                studentTable.getTablePath(),
                new HashMap<String, String>() {
                    {
                        put("studentId", "$.StudentId");
                        put("studentName", "$.StudentName");
                    }
                });
        mappings.put(
                teacherTable.getTablePath(),
                new HashMap<String, String>() {
                    {
                        put("teacherId", "$.TeacherId");
                        put("teacherName", "$.TeacherName");
                    }
                });

        CustomCdcJsonSourceDeserializationSchema schema =
                CustomCdcJsonSourceDeserializationSchema.builder(
                                java.util.Arrays.asList(studentTable, teacherTable))
                        .setJsonFieldMappings(mappings)
                        .setSchemaPath("$.schema")
                        .setTablePath("$.table")
                        .setOperationPath("$.operation")
                        .setBeforePath("$.message.beforeData")
                        .setAfterPath("$.message.afterData")
                        .build();

        ListCollector collector = new ListCollector();
        schema.deserialize(
                ("{"
                                + "\"schema\":\"public\","
                                + "\"table\":\"student\","
                                + "\"operation\":\"insert\","
                                + "\"message\":{\"beforeData\":null,"
                                + "\"afterData\":{\"StudentId\":\"1\",\"StudentName\":\"Alice\"}}"
                                + "}")
                        .getBytes(StandardCharsets.UTF_8),
                collector);

        schema.deserialize(
                ("{"
                                + "\"schema\":\"public\","
                                + "\"table\":\"teacher\","
                                + "\"operation\":\"update\","
                                + "\"message\":{\"beforeData\":{\"TeacherId\":\"9\",\"TeacherName\":\"Old\"},"
                                + "\"afterData\":{\"TeacherId\":\"9\",\"TeacherName\":\"New\"}}"
                                + "}")
                        .getBytes(StandardCharsets.UTF_8),
                collector);

        schema.deserialize(
                ("{"
                                + "\"schema\":\"public\","
                                + "\"table\":\"student\","
                                + "\"operation\":\"delete\","
                                + "\"message\":{\"beforeData\":{\"StudentId\":\"2\",\"StudentName\":\"Bob\"},"
                                + "\"afterData\":null}"
                                + "}")
                        .getBytes(StandardCharsets.UTF_8),
                collector);

        Assertions.assertEquals(4, collector.rows.size());

        SeaTunnelRow insertRow = collector.rows.get(0);
        Assertions.assertEquals("student", insertRow.getTableId());
        Assertions.assertEquals(RowKind.INSERT, insertRow.getRowKind());
        Assertions.assertEquals("1", insertRow.getField(0));
        Assertions.assertEquals("Alice", insertRow.getField(1));

        SeaTunnelRow updateBefore = collector.rows.get(1);
        Assertions.assertEquals("teacher", updateBefore.getTableId());
        Assertions.assertEquals(RowKind.UPDATE_BEFORE, updateBefore.getRowKind());
        Assertions.assertEquals("Old", updateBefore.getField(1));

        SeaTunnelRow updateAfter = collector.rows.get(2);
        Assertions.assertEquals("teacher", updateAfter.getTableId());
        Assertions.assertEquals(RowKind.UPDATE_AFTER, updateAfter.getRowKind());
        Assertions.assertEquals("New", updateAfter.getField(1));

        SeaTunnelRow deleteRow = collector.rows.get(3);
        Assertions.assertEquals("student", deleteRow.getTableId());
        Assertions.assertEquals(RowKind.DELETE, deleteRow.getRowKind());
        Assertions.assertEquals("2", deleteRow.getField(0));
    }

    @Test
    void shouldUseConfiguredOperationTypeMapping() {
        CatalogTable studentTable =
                CatalogTable.of(
                        TableIdentifier.of("", TablePath.of("student", true)),
                        TableSchema.builder()
                                .column(
                                        PhysicalColumn.of(
                                                "studentId",
                                                BasicType.STRING_TYPE,
                                                255L,
                                                true,
                                                null,
                                                null))
                                .build(),
                        new HashMap<>(),
                        new ArrayList<>(),
                        null);

        Map<TablePath, Map<String, String>> mappings = new HashMap<>();
        mappings.put(
                studentTable.getTablePath(),
                new HashMap<String, String>() {
                    {
                        put("studentId", "$.StudentId");
                    }
                });

        Map<String, String> operationTypeMapping = new HashMap<>();
        operationTypeMapping.put("I", "INSERT");
        operationTypeMapping.put("U", "UPDATE_AFTER");
        operationTypeMapping.put("B", "UPDATE_BEFORE");
        operationTypeMapping.put("D", "DELETE");

        CustomCdcJsonSourceDeserializationSchema schema =
                CustomCdcJsonSourceDeserializationSchema.builder(
                                Collections.singletonList(studentTable))
                        .setJsonFieldMappings(mappings)
                        .setSchemaPath("$.schema")
                        .setTablePath("$.table")
                        .setOperationPath("$.operation")
                        .setOperationTypeMapping(operationTypeMapping)
                        .setBeforePath("$.message.beforeData")
                        .setAfterPath("$.message.afterData")
                        .build();

        ListCollector collector = new ListCollector();
        schema.deserialize(
                ("{"
                                + "\"schema\":\"public\","
                                + "\"table\":\"student\","
                                + "\"operation\":\"I\","
                                + "\"message\":{\"beforeData\":null,"
                                + "\"afterData\":{\"StudentId\":\"1\"}}"
                                + "}")
                        .getBytes(StandardCharsets.UTF_8),
                collector);

        schema.deserialize(
                ("{"
                                + "\"schema\":\"public\","
                                + "\"table\":\"student\","
                                + "\"operation\":\"U\","
                                + "\"message\":{\"beforeData\":{\"StudentId\":\"1\"},"
                                + "\"afterData\":{\"StudentId\":\"2\"}}"
                                + "}")
                        .getBytes(StandardCharsets.UTF_8),
                collector);
        schema.deserialize(
                ("{"
                                + "\"schema\":\"public\","
                                + "\"table\":\"student\","
                                + "\"operation\":\"B\","
                                + "\"message\":{\"beforeData\":{\"StudentId\":\"3\"},"
                                + "\"afterData\":{\"StudentId\":\"4\"}}"
                                + "}")
                        .getBytes(StandardCharsets.UTF_8),
                collector);

        schema.deserialize(
                ("{"
                                + "\"schema\":\"public\","
                                + "\"table\":\"student\","
                                + "\"operation\":\"D\","
                                + "\"message\":{\"beforeData\":{\"StudentId\":\"2\"},"
                                + "\"afterData\":null}"
                                + "}")
                        .getBytes(StandardCharsets.UTF_8),
                collector);

        Assertions.assertEquals(4, collector.rows.size());
        Assertions.assertEquals(RowKind.INSERT, collector.rows.get(0).getRowKind());
        Assertions.assertEquals(RowKind.UPDATE_AFTER, collector.rows.get(1).getRowKind());
        Assertions.assertEquals("2", collector.rows.get(1).getField(0));
        Assertions.assertEquals(RowKind.UPDATE_BEFORE, collector.rows.get(2).getRowKind());
        Assertions.assertEquals("3", collector.rows.get(2).getField(0));
        Assertions.assertEquals(RowKind.DELETE, collector.rows.get(3).getRowKind());
    }

    @Test
    void shouldNotFallbackToSimpleTableWhenOnlySchemaQualifiedTableIsConfigured() {
        CatalogTable publicStudentTable =
                CatalogTable.of(
                        TableIdentifier.of("", TablePath.of("public.student")),
                        TableSchema.builder()
                                .column(
                                        PhysicalColumn.of(
                                                "studentId",
                                                BasicType.STRING_TYPE,
                                                255L,
                                                true,
                                                null,
                                                null))
                                .build(),
                        new HashMap<>(),
                        new ArrayList<>(),
                        null);

        Map<TablePath, Map<String, String>> mappings = new HashMap<>();
        mappings.put(
                publicStudentTable.getTablePath(),
                new HashMap<String, String>() {
                    {
                        put("studentId", "$.StudentId");
                    }
                });

        CustomCdcJsonSourceDeserializationSchema schema =
                CustomCdcJsonSourceDeserializationSchema.builder(
                                Collections.singletonList(publicStudentTable))
                        .setJsonFieldMappings(mappings)
                        .setSimpleTablePaths(Collections.emptySet())
                        .setSchemaPath("$.schema")
                        .setTablePath("$.table")
                        .setOperationPath("$.operation")
                        .setAfterPath("$.message.afterData")
                        .build();

        ListCollector collector = new ListCollector();
        schema.deserialize(
                ("{"
                                + "\"schema\":\"archive\","
                                + "\"table\":\"student\","
                                + "\"operation\":\"insert\","
                                + "\"message\":{\"afterData\":{\"StudentId\":\"1\"}}"
                                + "}")
                        .getBytes(StandardCharsets.UTF_8),
                collector);

        Assertions.assertTrue(collector.rows.isEmpty());
    }

    @Test
    void shouldSkipUnconfiguredSchemaQualifiedTableWhenSimpleNameIsAmbiguous() {
        CatalogTable tableA =
                CatalogTable.of(
                        TableIdentifier.of("", TablePath.of("a_db.attunity_accounts", true)),
                        TableSchema.builder()
                                .column(
                                        PhysicalColumn.of(
                                                "recordId",
                                                BasicType.STRING_TYPE,
                                                255L,
                                                true,
                                                null,
                                                null))
                                .build(),
                        new HashMap<>(),
                        new ArrayList<>(),
                        null);
        CatalogTable tableC =
                CatalogTable.of(
                        TableIdentifier.of("", TablePath.of("c_db.attunity_accounts", true)),
                        TableSchema.builder()
                                .column(
                                        PhysicalColumn.of(
                                                "recordId",
                                                BasicType.STRING_TYPE,
                                                255L,
                                                true,
                                                null,
                                                null))
                                .build(),
                        new HashMap<>(),
                        new ArrayList<>(),
                        null);

        Map<TablePath, Map<String, String>> mappings = new HashMap<>();
        mappings.put(
                tableA.getTablePath(),
                new HashMap<String, String>() {
                    {
                        put("recordId", "$.REC_ID");
                    }
                });
        mappings.put(
                tableC.getTablePath(),
                new HashMap<String, String>() {
                    {
                        put("recordId", "$.REC_ID");
                    }
                });

        CustomCdcJsonSourceDeserializationSchema schema =
                CustomCdcJsonSourceDeserializationSchema.builder(
                                java.util.Arrays.asList(tableA, tableC))
                        .setJsonFieldMappings(mappings)
                        .setSimpleTablePaths(Collections.emptySet())
                        .setSchemaPath("$.message.data.schema")
                        .setTablePath("$.message.data.tablename")
                        .setOperationPath("$.message.headers.operation")
                        .setBeforePath("$.message.beforeData")
                        .setAfterPath("$.message.data")
                        .build();

        ListCollector collector = new ListCollector();
        schema.deserialize(
                ("{"
                                + "\"message\":{"
                                + "\"data\":{\"schema\":\"b_db\",\"tablename\":\"attunity_accounts\",\"REC_ID\":\"B001\"},"
                                + "\"beforeData\":{},"
                                + "\"headers\":{\"operation\":\"INSERT\"}"
                                + "}"
                                + "}")
                        .getBytes(StandardCharsets.UTF_8),
                collector);
        schema.deserialize(
                ("{"
                                + "\"message\":{"
                                + "\"data\":{\"schema\":\"c_db\",\"tablename\":\"attunity_accounts\",\"REC_ID\":\"C001\"},"
                                + "\"beforeData\":{},"
                                + "\"headers\":{\"operation\":\"INSERT\"}"
                                + "}"
                                + "}")
                        .getBytes(StandardCharsets.UTF_8),
                collector);

        Assertions.assertEquals(1, collector.rows.size());
        Assertions.assertEquals("c_db.attunity_accounts", collector.rows.get(0).getTableId());
        Assertions.assertEquals("C001", collector.rows.get(0).getField(0));
        Assertions.assertEquals(RowKind.INSERT, collector.rows.get(0).getRowKind());
    }

    private static class ListCollector implements Collector<SeaTunnelRow> {
        private final List<SeaTunnelRow> rows = new ArrayList<>();
        private final Object checkpointLock = new Object();

        @Override
        public void collect(SeaTunnelRow record) {
            rows.add(record);
        }

        @Override
        public Object getCheckpointLock() {
            return checkpointLock;
        }
    }
}

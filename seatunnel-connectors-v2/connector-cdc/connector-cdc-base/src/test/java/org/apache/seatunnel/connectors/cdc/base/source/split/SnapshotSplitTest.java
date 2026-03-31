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

package org.apache.seatunnel.connectors.cdc.base.source.split;

import org.apache.seatunnel.api.table.catalog.TablePath;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.debezium.relational.TableId;

public class SnapshotSplitTest {

    /**
     * getTablePath() should return a TablePath with catalog as databaseName, schema as schemaName,
     * and table as tableName when tableId contains all three parts.
     */
    @Test
    public void testGetTablePathWithFullTableId() {
        SnapshotSplit split =
                new SnapshotSplit(
                        "split-0",
                        new TableId("mydb", "myschema", "mytable"),
                        null,
                        null,
                        null,
                        0,
                        1,
                        false,
                        null);

        TablePath tablePath = split.getTablePath();

        Assertions.assertNotNull(tablePath);
        Assertions.assertEquals("mydb", tablePath.getDatabaseName());
        Assertions.assertEquals("myschema", tablePath.getSchemaName());
        Assertions.assertEquals("mytable", tablePath.getTableName());
    }

    /**
     * getTablePath() should return null when tableId is null to maintain the same contract as the
     * SourceSplit interface default method.
     */
    @Test
    public void testGetTablePathWithNullTableId() {
        SnapshotSplit split =
                new SnapshotSplit("split-0", null, null, null, null, 0, 1, false, null);

        Assertions.assertNull(split.getTablePath());
    }

    /**
     * getTablePath() should handle null catalog and schema (e.g. MySQL has no schema), returning a
     * TablePath with only tableName set.
     */
    @Test
    public void testGetTablePathWithNullCatalogAndSchema() {
        SnapshotSplit split =
                new SnapshotSplit(
                        "split-0",
                        new TableId(null, null, "mytable"),
                        null,
                        null,
                        null,
                        0,
                        1,
                        false,
                        null);

        TablePath tablePath = split.getTablePath();

        Assertions.assertNotNull(tablePath);
        Assertions.assertNull(tablePath.getDatabaseName());
        Assertions.assertNull(tablePath.getSchemaName());
        Assertions.assertEquals("mytable", tablePath.getTableName());
    }

    /**
     * getTablePath() result must be consistent with TableId.toTablePath() to ensure key consistency
     * between SnapshotSplitAssigner.addTableSplit() and EnumeratorEventRecorder.recordEvent().
     */
    @Test
    public void testGetTablePathConsistentWithTableIdToTablePath() {
        TableId tableId = new TableId("mydb", "myschema", "mytable");
        SnapshotSplit split =
                new SnapshotSplit("split-0", tableId, null, null, null, 0, 1, false, null);

        Assertions.assertEquals(tableId.toTablePath(), split.getTablePath());
    }
}
